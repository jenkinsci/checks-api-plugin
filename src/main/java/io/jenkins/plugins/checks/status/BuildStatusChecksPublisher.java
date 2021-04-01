package io.jenkins.plugins.checks.status;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
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
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.util.JenkinsFacade;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * A publisher which publishes different statuses through the checks API based on the stage of the {@link Queue.Item}
 * or {@link Run}.
 */
public final class BuildStatusChecksPublisher {

    private static final Logger LOGGER = Logger.getLogger(BuildStatusChecksPublisher.class.getName());

    private BuildStatusChecksPublisher() {
    }

    private static final JenkinsFacade JENKINS = new JenkinsFacade();
    private static final AbstractStatusChecksProperties DEFAULT_PROPERTIES = new DefaultStatusCheckProperties();

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion, final String name, @CheckForNull final ChecksOutput output) {
        ChecksDetailsBuilder builder = new ChecksDetailsBuilder()
                .withName(name)
                .withStatus(status)
                .withConclusion(conclusion);

        if (output != null) {
            builder.withOutput(output);
        }

        publisher.publish(builder.build());
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

    static Optional<String> getChecksName(final Run<?, ?> run) {
        return getChecksName(run.getParent());
    }

    static Optional<String> getChecksName(final Job<?, ?> job) {
        return Stream.of(
                findDeprecatedProperties(job)
                        .filter(p -> !p.isSkip(job))
                        .map(p -> p.getName(job)),
                Optional.of(findProperties(job))
                        .filter(p -> !p.isSkipped(job))
                        .map(p -> p.getName(job))
        )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @CheckForNull
    static ChecksOutput getOutput(final Run<?, ?> run) {
        if (!(run instanceof FlowExecutionOwner.Executable)) {
            return null;
        }
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
        if (owner == null) {
            return null;
        }
        FlowExecution execution = owner.getOrNull();
        if (execution == null) {
            return null;
        }
        return getOutput(run, execution);
    }

    static ChecksOutput getOutput(final Run<?, ?> run, final FlowExecution execution) {
        return new FlowExecutionAnalyzer(run, execution, findProperties(run.getParent()).isSuppressLogs(run.getParent())).extractOutput();
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

            final Job<?, ?> job = (Job<?, ?>) wi.task;
            getChecksName(job).ifPresent(checksName -> runAsync(() -> {
                ChecksPublisher publisher = ChecksPublisherFactory.fromJob(job, TaskListener.NULL);
                publish(publisher, ChecksStatus.QUEUED, ChecksConclusion.NONE, checksName, null);
            }));
        }

        @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
        private void runAsync(Runnable run) {
            Computer.threadPoolForRemoting.submit(run);
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
            getChecksName(run).ifPresent(checksName -> publish(ChecksPublisherFactory.fromRun(run, listener),
                    ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE, checksName, null));
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
            getChecksName(run).ifPresent(checksName -> publish(ChecksPublisherFactory.fromRun(run, listener),
                    ChecksStatus.COMPLETED, extractConclusion(run), checksName, getOutput(run)));
        }

        private ChecksConclusion extractConclusion(final Run<?, ?> run) {
            Result result = run.getResult();
            if (result == null) {
                throw new IllegalStateException("No result when the run completes, run: " + run.toString());
            }

            Job<?, ?> job = run.getParent();
            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                return ChecksConclusion.SUCCESS;
            }
            else if (result.isBetterOrEqualTo(Result.UNSTABLE) && findProperties(job).isUnstableBuildNeutral(job)) {
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

    /**
     * {@inheritDoc}
     *
     * <p>
     * As a job progresses, record a representation of the flow graph.
     * </p>
     */
    @Extension
    public static class ChecksGraphListener implements GraphListener {
        @Override
        public void onNewHead(final FlowNode node) {
            if (node.getAction(LabelAction.class) == null) {
                // It's not a branch or stage node, so let's not worry about updating.
                return;
            }

            Run<?, ?> run;
            try {
                run = (Run<?, ?>) node.getExecution().getOwner().getExecutable();
            }
            catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to find Run from flow node.", e);
                return;
            }

            Job<?, ?> job = run.getParent();
            if (!findProperties(job).isSkipProgressUpdates(job)) {
                getChecksName(run).ifPresent(checksName -> publish(ChecksPublisherFactory.fromRun(run, TaskListener.NULL),
                    ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE, checksName, getOutput(run, node.getExecution())));
            }

        }
    }
}
