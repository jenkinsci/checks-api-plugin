package io.jenkins.plugins.checks.status;

import java.io.File;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.model.queue.QueueListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * A publisher which publishes different statuses through the checks API based on the stage of the {@link Queue.Item}
 * or {@link Run}.
 */
public final class BuildStatusChecksPublisher {
    private static final JenkinsFacade JENKINS = new JenkinsFacade();
    private static final AbstractStatusChecksProperties DEFAULT_PROPERTIES = new DefaultStatusCheckProperties();

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion, final String name) {
        publisher.publish(new ChecksDetailsBuilder()
                .withName(name)
                .withStatus(status)
                .withConclusion(conclusion)
                .build());
    }

    @Deprecated
    private static Optional<StatusChecksProperties> findDeprecatedProperties(final Job<?, ?> job) {
        return JENKINS.getExtensionsFor(StatusChecksProperties.class)
                .stream()
                .filter(p -> p.isApplicable(job))
                .findFirst();
    }

    private static AbstractStatusChecksProperties findProperties(final Job<?, ?> job) {
        return JENKINS.getExtensionsFor(AbstractStatusChecksProperties.class)
                .stream()
                .filter(p -> p.isApplicable(job))
                .findFirst()
                .orElse(DEFAULT_PROPERTIES);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Listens to the queue and publishes checks in "queued" state for entering items.
     * </p>
     */
    @Extension
    public static class JobScheduledListener extends QueueListener {
        /**
         * {@inheritDoc}
         *
         * <p>
         * When a job enters queue, creates the check on "queued".
         * </p>
         */
        @Override
        public void onEnterWaiting(final Queue.WaitingItem wi) {
            if (!(wi.task instanceof Job)) {
                return;
            }

            final Job job = (Job)wi.task;
            Optional<StatusChecksProperties> deprecatedProperties = findDeprecatedProperties(job);
            if (deprecatedProperties.isPresent()) {
                if (!deprecatedProperties.get().isSkip(job)) {
                    publish(ChecksPublisherFactory.fromJob(job, TaskListener.NULL), ChecksStatus.QUEUED,
                            ChecksConclusion.NONE, deprecatedProperties.get().getName(job));
                }
            }
            else {
                final AbstractStatusChecksProperties properties = findProperties(job);
                if (!properties.isSkipped(job)) {
                    publish(ChecksPublisherFactory.fromJob(job, TaskListener.NULL), ChecksStatus.QUEUED,
                            ChecksConclusion.NONE, properties.getName(job));
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Listens to the SCM checkout and publishes checks.
     * </p>
     */
    @Extension
    public static class JobCheckoutListener extends SCMListener {
        /**
         * {@inheritDoc}
         * <p>
         * When checkout finished, update the check to "in progress".
         * </p>
         */
        @Override
        public void onCheckout(final Run<?, ?> run, final SCM scm, final FilePath workspace,
                               final TaskListener listener, @CheckForNull final File changelogFile,
                               @CheckForNull final SCMRevisionState pollingBaseline) {
            final Job job = run.getParent();
            final Optional<StatusChecksProperties> deprecatedProperties = findDeprecatedProperties(job);
            if (deprecatedProperties.isPresent()) {
                if (!deprecatedProperties.get().isSkip(job)) {
                    publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.IN_PROGRESS,
                            ChecksConclusion.NONE, deprecatedProperties.get().getName(job));
                }
            }
            else {
                final AbstractStatusChecksProperties properties = findProperties(job);
                if (!properties.isSkipped(job)) {
                    publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.IN_PROGRESS,
                            ChecksConclusion.NONE, properties.getName(job));
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Listens to the run and publishes checks.
     * </p>
     */
    @Extension
    public static class JobCompletedListener extends RunListener<Run<?, ?>> {
        /**
         * {@inheritDoc}
         *
         * <p>
         * When a job completes, completes the check.
         * </p>
         */
        @Override
        public void onCompleted(final Run run, @CheckForNull final TaskListener listener) {
            final Job job = run.getParent();
            final Optional<StatusChecksProperties> deprecatedProperties = findDeprecatedProperties(job);
            if (deprecatedProperties.isPresent()) {
                if (!deprecatedProperties.get().isSkip(job)) {
                    publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.COMPLETED,
                            extractConclusion(run), deprecatedProperties.get().getName(job));
                }
            }
            else {
                final AbstractStatusChecksProperties properties = findProperties(job);
                if (!properties.isSkipped(job)) {
                    publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.COMPLETED,
                            extractConclusion(run), properties.getName(job));
                }
            }
        }

        private ChecksConclusion extractConclusion(final Run<?, ?> run) {
            Result result = run.getResult();
            if (result == null) {
                throw new IllegalStateException("No result when the run completes, run: " + run.toString());
            }

            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                return ChecksConclusion.SUCCESS;
            }
            else if (result.isBetterOrEqualTo(Result.UNSTABLE) || result.isBetterOrEqualTo(Result.FAILURE)) {
                return ChecksConclusion.FAILURE;
            }
            else if (result.isBetterOrEqualTo(Result.NOT_BUILT)) {
                return ChecksConclusion.SKIPPED;
            }
            else if (result.isBetterOrEqualTo(Result.ABORTED)) {
                return ChecksConclusion.CANCELED;
            }
            else {
                throw new IllegalStateException("Unsupported run result: " + result);
            }
        }
    }
}
