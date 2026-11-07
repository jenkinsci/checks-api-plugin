package io.jenkins.plugins.checks.status;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import hudson.init.Terminator;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import jenkins.security.ImpersonatingExecutorService;
import jenkins.util.SystemProperties;

import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;

/**
 * Publishes the checks of a pipeline run off the thread that requested them.
 *
 * <p>
 * A pipeline requests its checks from listeners that run on the build's CPS VM thread: progress updates from a
 * {@link org.jenkinsci.plugins.workflow.flow.GraphListener}, and the completed check from
 * {@link hudson.model.listeners.RunListener#onCompleted}, which {@code WorkflowRun} fires on that thread too.
 * Publishing makes blocking calls to the SCM API, so doing it on that thread stops the pipeline until every call
 * returns. This class moves the work to a small background pool instead.
 * </p>
 *
 * <p>
 * Each run has at most one progress update waiting and one being published. A new progress update replaces the
 * waiting one, and its output is computed when it is published, so it always shows the latest state.
 * </p>
 *
 * <p>
 * Completed checks have their own pool, so they never wait behind the progress updates of other runs. Completing a
 * run discards its waiting progress update, and the completed check is published after the one being published, if
 * any, so a progress update can never overwrite the completed check. Nothing waits on the calling thread.
 * </p>
 */
final class ChecksPublishingQueue {
    private static final Logger LOGGER = Logger.getLogger(ChecksPublishingQueue.class.getName());

    private static final int THREADS = SystemProperties.getInteger(
            ChecksPublishingQueue.class.getName() + ".threads", 4);

    private static final int COMPLETED_THREADS = SystemProperties.getInteger(
            ChecksPublishingQueue.class.getName() + ".completedThreads", 4);

    /** How long shutdown waits for queued checks, the completed ones in particular, to be published. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = SystemProperties.getInteger(
            ChecksPublishingQueue.class.getName() + ".shutdownTimeoutSeconds", 30);

    private static final Map<String, RunState> STATES = new ConcurrentHashMap<>();

    private static final ThreadPoolExecutor POOL = createPool(THREADS, "Checks API publisher");

    private static final ThreadPoolExecutor COMPLETED_POOL = createPool(COMPLETED_THREADS,
            "Checks API completed check publisher");

    // Replaced in tests only
    @VisibleForTesting
    static Executor executor = new ImpersonatingExecutorService(POOL, ACL.SYSTEM2);

    // Replaced in tests only
    @VisibleForTesting
    static Executor completedExecutor = new ImpersonatingExecutorService(COMPLETED_POOL, ACL.SYSTEM2);

    private ChecksPublishingQueue() {
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    private static ThreadPoolExecutor createPool(final int threads, final String name) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new NamingThreadFactory(new DaemonThreadFactory(), name));
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * Requests an {@code IN_PROGRESS} update for the run. Returns immediately.
     *
     * @param run
     *         the run to update
     * @param checksName
     *         the name of the check
     * @param output
     *         computes the output when the update is published
     */
    static void schedule(final Run<?, ?> run, final String checksName, final Supplier<ChecksOutput> output) {
        // Do not track a run that has already completed: a late notification would publish an update after the
        // completed check, and nothing would remove the entry again. WorkflowRun stops building before it notifies
        // the run listeners, and complete() removes the entry in the same map, so this check cannot miss it.
        RunState state = STATES.compute(run.getExternalizableId(), (key, existing) -> {
            if (existing != null) {
                return existing;
            }
            return run.isBuilding() ? new RunState() : null;
        });
        if (state != null) {
            state.request(new Request(run, checksName, ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE, output));
        }
    }

    /**
     * Completes the run: discards a waiting progress update, and publishes the completed check on its own pool, after
     * the progress update being published, if any. Returns immediately.
     *
     * @param run
     *         the completing run
     * @param checksName
     *         the name of the check, or {@code null} to publish no check
     * @param conclusion
     *         the conclusion of the run
     * @param output
     *         computes the output when the check is published
     */
    static void complete(final Run<?, ?> run, @CheckForNull final String checksName,
            final ChecksConclusion conclusion, final Supplier<ChecksOutput> output) {
        RunState state = STATES.remove(run.getExternalizableId());
        if (state == null) {
            state = new RunState();
        }
        state.complete(checksName == null
                ? null
                : new Request(run, checksName, ChecksStatus.COMPLETED, conclusion, output));
    }

    @VisibleForTesting
    static int getTrackedRunCount() {
        return STATES.size();
    }

    /**
     * Gives queued checks some time to be published when Jenkins shuts down. A completed check that is not published
     * leaves the check in progress on the SCM for good.
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     */
    @Terminator
    @SuppressWarnings("unused")
    public static void shutdown() throws InterruptedException {
        POOL.shutdown();
        COMPLETED_POOL.shutdown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_TIMEOUT_SECONDS);
        // Completed checks matter most, so wait for them first
        for (ThreadPoolExecutor pool : new ThreadPoolExecutor[] {COMPLETED_POOL, POOL}) {
            if (!pool.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                LOGGER.log(Level.WARNING, "Gave up waiting for {0} queued checks to be published",
                        pool.getQueue().size() + pool.getActiveCount());
            }
        }
    }

    /**
     * Publishing state of one run.
     *
     * <p>
     * {@code stateLock} guards the fields and is only held briefly, so requests never block the calling thread.
     * {@code publishLock} is held while publishing, so a run never publishes two checks at the same time, and the
     * completed check waits for a progress update that is being published.
     * </p>
     *
     * <p>
     * Only the waiting {@link Request} references the run, and completion clears it, so a progress task that is
     * still queued for a completed run does nothing and does not keep the run in memory.
     * </p>
     */
    private static final class RunState {
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final Object stateLock = new Object();
        private final Object publishLock = new Object();

        // Guarded by stateLock
        private boolean completed;
        @CheckForNull
        private Request pending;

        void request(final Request request) {
            synchronized (stateLock) {
                if (completed) {
                    return;
                }
                pending = request;
            }
            scheduleIfPending();
        }

        void complete(@CheckForNull final Request finalRequest) {
            synchronized (stateLock) {
                completed = true;
                // A progress update that is still waiting is obsolete now
                pending = null;
            }
            if (finalRequest != null) {
                execute(completedExecutor, () -> {
                    synchronized (publishLock) {
                        finalRequest.publish();
                    }
                });
            }
        }

        private void scheduleIfPending() {
            if (hasPending() && scheduled.compareAndSet(false, true) && !execute(executor, this::run)) {
                scheduled.set(false);
            }
        }

        private static boolean execute(final Executor target, final Runnable task) {
            try {
                target.execute(task);
                return true;
            }
            catch (RejectedExecutionException e) {
                LOGGER.log(Level.WARNING, "Jenkins is shutting down, not publishing check", e);
                return false;
            }
        }

        /**
         * Publishes one check per task. If another one was requested meanwhile, a new task goes to the end of the
         * queue, so a busy run cannot keep a thread from the runs waiting behind it.
         */
        private void run() {
            try {
                publishPending();
            }
            finally {
                scheduled.set(false);
            }
            // A request that arrived while publishing did not submit a task, so submit it now.
            scheduleIfPending();
        }

        private boolean hasPending() {
            synchronized (stateLock) {
                return pending != null;
            }
        }

        private void publishPending() {
            synchronized (publishLock) {
                Request request;
                synchronized (stateLock) {
                    request = pending;
                    pending = null;
                }
                if (request != null) {
                    request.publish();
                }
            }
        }
    }

    /**
     * A check waiting to be published.
     */
    private static final class Request {
        private final Run<?, ?> run;
        private final String checksName;
        private final ChecksStatus status;
        private final ChecksConclusion conclusion;
        private final Supplier<ChecksOutput> output;

        Request(final Run<?, ?> run, final String checksName, final ChecksStatus status,
                final ChecksConclusion conclusion, final Supplier<ChecksOutput> output) {
            this.run = run;
            this.checksName = checksName;
            this.status = status;
            this.conclusion = conclusion;
            this.output = output;
        }

        @SuppressWarnings({"PMD.AvoidCatchingGenericException", "IllegalCatch"})
        void publish() {
            try {
                // The build log may be closed by now, so do not log to it
                BuildStatusChecksPublisher.publish(ChecksPublisherFactory.fromRun(run, TaskListener.NULL), status,
                        conclusion, checksName, output.get());
            }
            catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to publish " + status + " check for " + run, e);
            }
        }
    }
}
