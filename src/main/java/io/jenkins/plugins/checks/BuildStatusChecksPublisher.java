package io.jenkins.plugins.checks;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
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

import java.io.File;

/**
 * A publisher which publishes different statuses through the checks API based on the stage of the {@link Queue.Item}
 * or {@link Run}.
 */
public class BuildStatusChecksPublisher {
    private static final String CHECKS_NAME = "Jenkins";

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion) {
        publisher.publish(new ChecksDetailsBuilder()
                .withName(CHECKS_NAME)
                .withStatus(status)
                .withConclusion(conclusion)
                .build());
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

            publish(ChecksPublisherFactory.fromJob((Job) wi.task, TaskListener.NULL), ChecksStatus.QUEUED,
                    ChecksConclusion.NONE);
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
                               final TaskListener listener, @Nullable final File changelogFile,
                               @Nullable final SCMRevisionState pollingBaseline) {
            publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE);
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
        public void onCompleted(final Run run, @Nullable final TaskListener listener) {
            publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.COMPLETED, extractConclusion(run));
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
