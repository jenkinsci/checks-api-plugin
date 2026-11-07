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

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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
// Public only so that Jenkins can call the @Terminator method
@Restricted(NoExternalUse.class)
public final class ChecksPublishingQueue {
    private static final Logger LOGGER = Logger.getLogger(ChecksPublishingQueue.class.getName());

    private static final int THREADS = SystemProperties.getInteger(
            ChecksPublishingQueue.class.getName() + ".threads", 4);

    /**
     * There is one completed check per build, and builds often finish in bursts, for example the branches of one
     * change. Idle threads expire, so a larger pool costs little.
     */
    private static final int COMPLETED_THREADS = SystemProperties.getInteger(
            ChecksPublishingQueue.class.getName() + ".completedThreads", 8);

    /** How long shutdown waits for queued completed checks to be published. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = SystemProperties.getInteger(
            ChecksPublishingQueue.class.getName() + ".shutdownTimeoutSeconds", 30);

    private static final Map<String, RunState> STATES = new ConcurrentHashMap<>();

    /**
     * Replaced with new pools on shutdown, so that a Jenkins started again in the same JVM, as in tests, can publish.
     * The pools create threads only when needed.
     */
    private static volatile Pools pools = new Pools();

    // Replaced in tests only
    @VisibleForTesting
    static Executor executor = task -> pools.progressExecutor.execute(task);

    // Replaced in tests only
    @VisibleForTesting
    static Executor completedExecutor = task -> pools.completedExecutor.execute(task);

    private ChecksPublishingQueue() {
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
     * Stops publishing when Jenkins shuts down. Progress updates are dropped, as the completed check replaces them
     * anyway. Completed checks get some time to be published: one that is lost leaves the check in progress on the
     * SCM for good.
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     */
    @Terminator
    @SuppressWarnings("unused")
    public static void shutdown() throws InterruptedException {
        Pools stopping = pools;
        pools = new Pools();
        // Also interrupts a progress update being published, which a completed check of the same run may wait for
        stopping.progress.shutdownNow();
        stopping.completed.shutdown();
        if (!stopping.completed.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            LOGGER.log(Level.WARNING, "Gave up waiting for {0} completed checks to be published",
                    stopping.completed.getQueue().size() + stopping.completed.getActiveCount());
        }
    }

    /**
     * The thread pools that publish progress updates and completed checks.
     */
    private static final class Pools {
        private final ThreadPoolExecutor progress = createPool(THREADS, "Checks API publisher");
        private final ThreadPoolExecutor completed = createPool(COMPLETED_THREADS,
                "Checks API completed check publisher");
        private final Executor progressExecutor = new ImpersonatingExecutorService(progress, ACL.SYSTEM2);
        private final Executor completedExecutor = new ImpersonatingExecutorService(completed, ACL.SYSTEM2);

        @SuppressWarnings("PMD.DoNotUseThreads")
        private static ThreadPoolExecutor createPool(final int threads, final String name) {
            ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new NamingThreadFactory(new DaemonThreadFactory(), name));
            pool.allowCoreThreadTimeOut(true);
            return pool;
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
                }, Level.WARNING);
            }
        }

        private void scheduleIfPending() {
            if (hasPending() && scheduled.compareAndSet(false, true) && !execute(executor, this::run, Level.FINE)) {
                scheduled.set(false);
            }
        }

        private static boolean execute(final Executor target, final Runnable task, final Level level) {
            try {
                target.execute(task);
                return true;
            }
            catch (RejectedExecutionException e) {
                LOGGER.log(level, "Jenkins is shutting down, not publishing check", e);
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
