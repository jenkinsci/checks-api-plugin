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
final public class BuildStatusChecksPublisher {
    private static final JenkinsFacade jenkins = new JenkinsFacade();
    private static final StatusChecksProperties defaultProperties = new DefaultStatusCheckProperties();

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion, final StatusChecksProperties properties) {
        if (properties.isActive()) {
            publisher.publish(new ChecksDetailsBuilder()
                    .withName(properties.getName())
                    .withStatus(status)
                    .withConclusion(conclusion)
                    .build());
        }
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

            publish(ChecksPublisherFactory.fromJob((Job)wi.task, TaskListener.NULL),
                    ChecksStatus.QUEUED, ChecksConclusion.NONE,
                    jenkins.getExtensionsFor(StatusChecksProperties.class)
                            .stream()
                            .filter(properties -> properties.isApplicable((Job)wi.task))
                            .findFirst()
                            .orElse(defaultProperties));
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
            publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE,
                    jenkins.getExtensionsFor(StatusChecksProperties.class)
                            .stream()
                            .filter(properties -> properties.isApplicable(run))
                            .findFirst()
                            .orElse(defaultProperties));
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
            publish(ChecksPublisherFactory.fromRun(run, listener), ChecksStatus.COMPLETED, extractConclusion(run),
                    jenkins.getExtensionsFor(StatusChecksProperties.class)
                            .stream()
                            .filter(properties -> properties.isApplicable(run))
                            .findFirst()
                            .orElse(defaultProperties));
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

class DefaultStatusCheckProperties implements StatusChecksProperties {
    @Override
    public String getName() {
        return "Jenkins";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isApplicable(final Job<?, ?> job) {
        return false;
    }

    @Override
    public boolean isApplicable(final Run<?, ?> run) {
        return false;
    }
}
