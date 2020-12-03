package io.jenkins.plugins.checks.status;

import java.io.File;

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
    private static final StatusChecksProperties DEFAULT_PROPERTIES = new DefaultStatusCheckProperties();

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion, final String name) {
        publisher.publish(new ChecksDetailsBuilder()
                .withName(name)
                .withStatus(status)
                .withConclusion(conclusion)
                .build());
    }

    private static StatusChecksProperties findProperties(final Job<?, ?> job) {
        return JENKINS.getExtensionsFor(StatusChecksProperties.class)
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
            final StatusChecksProperties properties = findProperties(job);
            if (!properties.isSkip(job)) {
                publish(ChecksPublisherFactory.fromJob(job, TaskListener.NULL), ChecksStatus.QUEUED,
                        ChecksConclusion.NONE, properties.getName(job));
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
            final StatusChecksProperties properties = findProperties(run.getParent());

            if (!properties.isSkip(run.getParent())) {
                publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE,
                        properties.getName(run.getParent()));
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
            final StatusChecksProperties properties = findProperties(run.getParent());

            if (!properties.isSkip(run.getParent())) {
                publish(
                    ChecksPublisherFactory.fromRun(run, listener),
                    ChecksStatus.COMPLETED,
                    extractConclusion(run, properties.isUnstableNeutral()),
                    properties.getName(run.getParent())
                );
            }
        }

        private ChecksConclusion extractConclusion(final Run<?, ?> run, boolean isUnstableNeutral) {
            Result result = run.getResult();
            if (result == null) {
                throw new IllegalStateException("No result when the run completes, run: " + run.toString());
            }

            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                return ChecksConclusion.SUCCESS;
            }
            else if (result.isBetterOrEqualTo(Result.UNSTABLE)) {
                return isUnstableNeutral ? ChecksConclusion.NEUTRAL : ChecksConclusion.FAILURE;
            }
            else if (result.isBetterOrEqualTo(Result.FAILURE)) {
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
