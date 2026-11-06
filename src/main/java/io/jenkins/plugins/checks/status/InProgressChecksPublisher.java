package io.jenkins.plugins.checks.status;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import jenkins.security.ImpersonatingExecutorService;
import jenkins.util.SystemProperties;

import io.jenkins.plugins.checks.api.ChecksOutput;

/**
 * Publishes {@code IN_PROGRESS} checks for a run off the thread that requested them.
 *
 * <p>
 * Progress updates are requested from {@link org.jenkinsci.plugins.workflow.flow.GraphListener}s, which run on the
 * build's CPS VM thread. Publishing makes blocking calls to the SCM API, so doing it on that thread stops the
 * pipeline until every call returns. This class moves the work to a small background pool instead.
 * </p>
 *
 * <p>
 * Updates are coalesced per run: while one update for a run is waiting or being published, further requests only
 * mark the run as dirty, and the output is computed when the update runs, so it always shows the latest state.
 * Once the run completes, {@link #complete(Run)} discards any pending update and waits for one that is being
 * published, so an {@code IN_PROGRESS} update can never overwrite the final {@code COMPLETED} one.
 * </p>
 */
final class InProgressChecksPublisher {
    private static final Logger LOGGER = Logger.getLogger(InProgressChecksPublisher.class.getName());

    private static final int THREADS = SystemProperties.getInteger(
            InProgressChecksPublisher.class.getName() + ".threads", 4);

    private static final Map<String, RunState> STATES = new ConcurrentHashMap<>();

    // Replaced in tests only
    @VisibleForTesting
    static Executor executor = createExecutor();

    private InProgressChecksPublisher() {
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    private static ExecutorService createExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(THREADS, THREADS, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new NamingThreadFactory(new DaemonThreadFactory(), "Checks API in-progress publisher"));
        pool.allowCoreThreadTimeOut(true);
        return new ImpersonatingExecutorService(pool, ACL.SYSTEM2);
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
            state.request(new Request(run, checksName, output));
        }
    }

    /**
     * Stops progress updates for a completing run. Discards a pending update and waits for one that is currently
     * being published, so that the caller can publish the final check afterwards.
     *
     * @param run
     *         the completing run
     */
    static void complete(final Run<?, ?> run) {
        RunState state = STATES.remove(run.getExternalizableId());
        if (state != null) {
            state.complete();
        }
    }

    @VisibleForTesting
    static int getTrackedRunCount() {
        return STATES.size();
    }

    /**
     * Progress update state of one run.
     *
     * <p>
     * {@code stateLock} guards the fields and is only held briefly, so {@link #request} never blocks the CPS VM
     * thread. {@code publishLock} is held while publishing, so {@link #complete()} waits for an update that is
     * being published.
     * </p>
     *
     * <p>
     * Only the pending {@link Request} references the run. {@link #complete()} clears it, so a task that is still
     * queued for a completed run does not keep the run in memory.
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

        void complete() {
            synchronized (stateLock) {
                completed = true;
                pending = null;
            }
            synchronized (publishLock) {
                // Wait for an update that is being published to finish.
                LOGGER.finest("Stopped progress updates");
            }
        }

        private void scheduleIfPending() {
            if (hasPending() && scheduled.compareAndSet(false, true)) {
                executor.execute(this::run);
            }
        }

        /**
         * Publishes one update per task. If another update was requested meanwhile, a new task goes to the end of
         * the queue, so a busy run cannot keep a thread from the runs waiting behind it.
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
                return pending != null && !completed;
            }
        }

        @SuppressWarnings({"PMD.AvoidCatchingGenericException", "IllegalCatch"})
        private void publishPending() {
            synchronized (publishLock) {
                Request request;
                synchronized (stateLock) {
                    request = pending;
                    if (completed || request == null) {
                        return;
                    }
                    pending = null;
                }
                try {
                    BuildStatusChecksPublisher.publishInProgress(request.run, request.checksName,
                            request.output.get());
                }
                catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Failed to publish in progress check for " + request.run, e);
                }
            }
        }
    }

    /**
     * A pending progress update.
     */
    private static final class Request {
        private final Run<?, ?> run;
        private final String checksName;
        private final Supplier<ChecksOutput> output;

        Request(final Run<?, ?> run, final String checksName, final Supplier<ChecksOutput> output) {
            this.run = run;
            this.checksName = checksName;
            this.output = output;
        }
    }
}
