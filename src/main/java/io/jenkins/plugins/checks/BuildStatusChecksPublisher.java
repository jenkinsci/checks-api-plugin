package io.jenkins.plugins.checks;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import hudson.model.listeners.RunListener;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;

/**
 * A publisher which publishes different statuses through the checks API based on the stage of the {@link Queue.Item}
 * or {@link Run}.
 */
public class BuildStatusChecksPublisher {
    private static final String CHECKS_NAME = "Jenkins";

    /**
     * {@inheritDoc}
     *
     * Listens to the queue and publishes checks in "queued" state for entering items.
     */
    @Extension
    public static class JobScheduledListener extends QueueListener {
        /**
         * {@inheritDoc}
         *
         * When a job enters queue, creates the check on "queued".
         */
        @Override
        public void onEnterWaiting(Queue.WaitingItem wi) {
            publish(ChecksPublisherFactory.fromItem(wi), ChecksStatus.QUEUED, ChecksConclusion.NONE);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Listens to the run and publishes checks for started and completed run.
     */
    @Extension
    public static class JobStartedListener extends RunListener<Run<?, ?>> {
        /**
         * {@inheritDoc}
         *
         * When a job starts, updates the check to "in progress".
         */
        @Override
        public void onStarted(final Run run, final TaskListener listener) {
            publish(ChecksPublisherFactory.fromRun(run), ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE);
        }

        /**
         * {@inheritDoc}
         *
         * When a job completes, completes the check.
         */
        @Override
        public void onCompleted(final Run run, @NonNull final TaskListener listener) {
            publish(ChecksPublisherFactory.fromRun(run), ChecksStatus.COMPLETED, extractConclusion(run));
        }

        private ChecksConclusion extractConclusion(final Run<?, ?> run) {
            Result result = run.getResult();
            if (result == null) {
                throw new IllegalStateException("No result when the run completes, run: " + run.toString());
            }

            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                return ChecksConclusion.SUCCESS;
            }
            else if (result.isBetterOrEqualTo(Result.UNSTABLE)) {
                return ChecksConclusion.NEUTRAL;
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

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion) {
        publisher.publish(new ChecksDetailsBuilder()
                .withName(CHECKS_NAME)
                .withStatus(status)
                .withConclusion(conclusion)
                .build());
    }
}
