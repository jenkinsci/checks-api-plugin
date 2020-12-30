package io.jenkins.plugins.checks.status;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.util.JenkinsFacade;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.views.FlowGraphTableAction;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * A publisher which publishes different statuses through the checks API based on the stage of the {@link Queue.Item}
 * or {@link Run}.
 */
public final class BuildStatusChecksPublisher {
    private static final JenkinsFacade JENKINS = new JenkinsFacade();
    private static final AbstractStatusChecksProperties DEFAULT_PROPERTIES = new DefaultStatusCheckProperties();

    private static void publish(final ChecksPublisher publisher, final ChecksStatus status,
                                final ChecksConclusion conclusion, final String name, final String outputSummary) {
        ChecksDetailsBuilder builder = new ChecksDetailsBuilder()
                .withName(name)
                .withStatus(status)
                .withConclusion(conclusion);

        if (StringUtils.isNotEmpty(outputSummary)) {
            builder.withOutput(new ChecksOutput.ChecksOutputBuilder()
                    .withTitle(name)
                    .withSummary(outputSummary)
                    .build());
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

            final Job job = (Job) wi.task;
            getChecksName(job).ifPresent(checksName -> publish(ChecksPublisherFactory.fromJob(job, TaskListener.NULL),
                    ChecksStatus.QUEUED, ChecksConclusion.NONE, checksName, null));
        }
    }

    protected static Optional<String> getChecksName(Run<?, ?> run) {
        return getChecksName(run.getParent());
    }

    protected static Optional<String> getChecksName(Job<?, ?> job) {
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
                    ChecksStatus.COMPLETED, extractConclusion(run), checksName, getOutputSummary(run)));
        }

        private ChecksConclusion extractConclusion(final Run<?, ?> run) {
            Result result = run.getResult();
            if (result == null) {
                throw new IllegalStateException("No result when the run completes, run: " + run.toString());
            }

            if (result.isBetterOrEqualTo(Result.SUCCESS)) {
                return ChecksConclusion.SUCCESS;
            } else if (result.isBetterOrEqualTo(Result.UNSTABLE) || result.isBetterOrEqualTo(Result.FAILURE)) {
                return ChecksConclusion.FAILURE;
            } else if (result.isBetterOrEqualTo(Result.NOT_BUILT)) {
                return ChecksConclusion.SKIPPED;
            } else if (result.isBetterOrEqualTo(Result.ABORTED)) {
                return ChecksConclusion.CANCELED;
            } else {
                throw new IllegalStateException("Unsupported run result: " + result);
            }
        }
    }

    private static boolean isStage(FlowNode node) {
        return node !=null && node.getAction(LabelAction.class) != null &&
                node.getAction(ThreadNameAction.class) == null;
    }

    private static boolean isParallelBranch(FlowNode node) {
        return node != null && node.getAction(LabelAction.class) != null &&
                node.getAction(ThreadNameAction.class) != null;

    }

    protected static String getOutputSummary(Run<?, ?> run) {
        FlowGraphTableAction flowGraphTableAction = run.getAction(FlowGraphTableAction.class);

        if (flowGraphTableAction == null) {
            return null;
        }

        FlowGraphTable table = flowGraphTableAction.getFlowGraph();

        Stack<Integer> indentationStack = new Stack<>();

        StringBuilder builder = new StringBuilder();

        table.getRows()
                .forEach(row -> {
                    boolean isStage = isStage(row.getNode());
                    boolean isParallel = isParallelBranch(row.getNode());
                    ErrorAction errorAction = row.getNode().getError();
                    WarningAction warningAction = row.getNode().getPersistentAction(WarningAction.class);

                    if (!isStage &&
                            !isParallel &&
                            errorAction == null &&
                            warningAction == null) {
                        return;
                    }

                    if (isStage || isParallel) {
                        while (!indentationStack.isEmpty() && row.getTreeDepth() < indentationStack.peek()) {
                            indentationStack.pop();
                        }
                        if (indentationStack.isEmpty() || row.getTreeDepth() > indentationStack.peek()) {
                            indentationStack.push(row.getTreeDepth());
                        }
                        builder.append(String.join("", Collections.nCopies(indentationStack.size(), "  ")));
                        builder.append("* ");

                        final String displayName;
                        if (isParallel) {
                            displayName = row.getNode().getAction(ThreadNameAction.class).getThreadName();
                        } else {
                            displayName = row.getNode().getDisplayName();
                        }
                        builder.append(displayName);

                        if (row.getNode().isActive()) {
                            builder.append(" *(running)*");
                        } else if (row.getDurationMillis() > 0) {
                            builder.append(String.format(" *(%s)*", row.getDurationString()));
                        }
                    } else {
                        builder.append(String.join("", Collections.nCopies(indentationStack.size() + 1, "  ")));
                        if (errorAction != null) {
                            builder.append(String.format("**Error**: *%s*", errorAction.getError()));
                        } else {
                            builder.append(String.format("**Unstable**: *%s*", warningAction.getMessage()));
                        }
                    }
                    builder.append("\n");

                });
        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Extension
    public static class ChecksGraphListener implements GraphListener {
        @Override
        public void onNewHead(FlowNode node) {
            if (!isStage(node) && !isParallelBranch(node)) {
                return;
            }

            Run<?, ?> run;
            try {
                run = (Run) node.getExecution().getOwner().getExecutable();
            } catch (IOException e) {
                return;
            }

            getChecksName(run).ifPresent(checksName -> publish(ChecksPublisherFactory.fromRun(run, TaskListener.NULL),
                    ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE, checksName, getOutputSummary(run)));

        }
    }
}
