package io.jenkins.plugins.checks.status;

import java.io.IOException;
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

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
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
// Public only so that Jenkins can call the extension and the @Initializer
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

    /** How long shutdown waits for completed checks to be published. */
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
     * SCM for good. Builds that complete meanwhile are published too.
     *
     * <p>
     * The pools stay shut down until Jenkins starts again, see {@link #start()}.
     * </p>
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     */
    @VisibleForTesting
    static void shutdown() throws InterruptedException {
        Pools stopping = pools;
        // Drop the waiting progress updates; new ones are rejected from now on. Do not interrupt one that is being
        // published: it may have created the check on the SCM already, and without its ID stored the completed check
        // would create a second one.
        stopping.progress.shutdown();
        stopping.progress.getQueue().clear();

        // A completed check waits for a progress update of its run that is being published, so wait for those too
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_TIMEOUT_SECONDS);
        while ((!stopping.progress.isTerminated() || pendingCount(stopping.completed) > 0)
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }

        int lost = stopping.completed.shutdownNow().size() + stopping.completed.getActiveCount();
        if (lost > 0) {
            LOGGER.log(Level.WARNING, "Gave up waiting for {0} completed checks to be published", lost);
        }
        STATES.clear();
    }

    private static int pendingCount(final ThreadPoolExecutor pool) {
        return pool.getQueue().size() + pool.getActiveCount();
    }

    /**
     * Creates new pools if a previous Jenkins in the same JVM shut them down, as in tests.
     */
    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void start() {
        if (pools.isShutdown()) {
            pools = new Pools();
        }
    }

    /**
     * Waits for completed checks when Jenkins shuts down.
     *
     * <p>
     * This runs before the terminators. Waiting in a terminator instead would break builds: the terminators of the
     * pipeline plugins pause the running pipelines and stop the threads that wait for {@code sh} steps first, so a
     * step that finishes while Jenkins waits fails the build.
     * </p>
     */
    @Extension
    public static class ShutdownListener extends ItemListener {
        @Override
        public void onBeforeShutdown() {
            try {
                shutdown();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

        boolean isShutdown() {
            return progress.isShutdown() || completed.isShutdown();
        }

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
     * The lock guards the fields and is only held briefly, so requests never block the calling thread, and no thread
     * ever waits for a publish. At most one progress task per run is queued or running, see {@code scheduled}, so a
     * run never publishes two progress updates at the same time.
     * </p>
     *
     * <p>
     * When the run completes while one of its progress updates is being published, the completed check is parked
     * until that publish is done, and then handed to the completed pool. So it is published after the progress
     * update, and a completed-pool thread never waits for a progress update.
     * </p>
     *
     * <p>
     * Only the waiting {@link Request}s reference the run, and completion clears the progress one, so a progress
     * task that is still queued for a completed run does nothing and does not keep the run in memory.
     * </p>
     */
    private static final class RunState {
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final Object lock = new Object();

        // Guarded by lock
        private boolean completed;
        @CheckForNull
        private Request pending;
        private boolean publishing;
        @CheckForNull
        private Request parkedCompletion;

        void request(final Request request) {
            synchronized (lock) {
                if (completed) {
                    return;
                }
                pending = request;
            }
            scheduleIfPending();
        }

        void complete(@CheckForNull final Request finalRequest) {
            Request submitNow = null;
            synchronized (lock) {
                completed = true;
                // A progress update that is still waiting is obsolete now
                pending = null;
                if (publishing) {
                    parkedCompletion = finalRequest;
                }
                else {
                    submitNow = finalRequest;
                }
            }
            submitCompletion(submitNow);
        }

        private static void submitCompletion(@CheckForNull final Request request) {
            if (request != null) {
                execute(completedExecutor, request::publish, Level.WARNING);
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
         * Publishes one progress update per task. If another one was requested meanwhile, a new task goes to the end
         * of the queue, so a busy run cannot keep a thread from the runs waiting behind it.
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
            synchronized (lock) {
                return pending != null;
            }
        }

        private void publishPending() {
            Request request;
            synchronized (lock) {
                request = pending;
                pending = null;
                if (request == null) {
                    return;
                }
                publishing = true;
            }
            Request completion;
            try {
                request.publish();
            }
            finally {
                synchronized (lock) {
                    publishing = false;
                    completion = parkedCompletion;
                    parkedCompletion = null;
                }
            }
            submitCompletion(completion);
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
            if (status == ChecksStatus.COMPLETED) {
                saveRun();
            }
        }

        /**
         * Saves what the publisher added to the run, as Jenkins saved it for the last time when it completed, before
         * this check was published. For example, github-checks adds the ID of a check it creates as an action, and
         * without it a later publication for the run would create a second check.
         */
        private void saveRun() {
            // Saving a deleted build would create its directory again
            if (!run.getRootDir().isDirectory()) {
                return;
            }
            try {
                run.save();
            }
            catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to save " + run + " after publishing its completed check", e);
            }
        }
    }
}
