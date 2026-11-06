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
        RunState state = STATES.computeIfAbsent(run.getExternalizableId(), k -> new RunState(run));
        state.request(checksName, output);
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
     */
    private static final class RunState {
        private final Run<?, ?> run;
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final Object stateLock = new Object();
        private final Object publishLock = new Object();

        // Guarded by stateLock
        private boolean completed;
        @CheckForNull
        private Request pending;

        RunState(final Run<?, ?> run) {
            this.run = run;
        }

        void request(final String name, final Supplier<ChecksOutput> outputSupplier) {
            synchronized (stateLock) {
                if (completed) {
                    return;
                }
                pending = new Request(name, outputSupplier);
            }
            if (scheduled.compareAndSet(false, true)) {
                executor.execute(this::drain);
            }
        }

        void complete() {
            synchronized (stateLock) {
                completed = true;
                pending = null;
            }
            synchronized (publishLock) {
                // Wait for an update that is being published to finish.
                LOGGER.log(Level.FINEST, "Stopped progress updates for {0}", run);
            }
        }

        private void drain() {
            do {
                try {
                    publishPending();
                }
                finally {
                    scheduled.set(false);
                }
                // A request may arrive after the last publish but before scheduled is reset. It does not submit a
                // task then, so pick it up here.
            } while (hasPending() && scheduled.compareAndSet(false, true));
        }

        private boolean hasPending() {
            synchronized (stateLock) {
                return pending != null && !completed;
            }
        }

        @SuppressWarnings({"PMD.AvoidCatchingGenericException", "IllegalCatch"})
        private void publishPending() {
            synchronized (publishLock) {
                while (true) {
                    Request request;
                    synchronized (stateLock) {
                        request = pending;
                        if (completed || request == null) {
                            return;
                        }
                        pending = null;
                    }
                    try {
                        BuildStatusChecksPublisher.publishInProgress(run, request.checksName, request.output.get());
                    }
                    catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "Failed to publish in progress check for " + run, e);
                    }
                }
            }
        }
    }

    /**
     * A pending progress update.
     */
    private static final class Request {
        private final String checksName;
        private final Supplier<ChecksOutput> output;

        Request(final String checksName, final Supplier<ChecksOutput> output) {
            this.checksName = checksName;
            this.output = output;
        }
    }
}
