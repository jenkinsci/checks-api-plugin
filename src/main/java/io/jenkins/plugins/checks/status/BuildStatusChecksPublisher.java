package io.jenkins.plugins.checks.status;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.*;
import hudson.model.Queue;
import hudson.model.listeners.RunListener;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.checks.api.*;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.util.JenkinsFacade;
import org.apache.commons.jelly.tags.xml.CopyTag;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.checkerframework.checker.nullness.Opt;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.views.FlowGraphTableAction;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A publisher which publishes different statuses through the checks API based on the stage of the {@link Queue.Item}
 * or {@link Run}.
 */
public final class BuildStatusChecksPublisher {

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

    private static boolean isStage(final FlowNode node) {
        return node != null && node.getAction(LabelAction.class) != null
                && node.getAction(ThreadNameAction.class) == null;
    }

    private static boolean isParallelBranch(final FlowNode node) {
        return node != null && node.getAction(LabelAction.class) != null
                && node.getAction(ThreadNameAction.class) != null;

    }

    private static Optional<String> getStageOrBranchName(final FlowNode node) {
        return Stream.of(
                Optional.ofNullable(node.getAction(ThreadNameAction.class)).map(ThreadNameAction::getThreadName),
                Optional.ofNullable(node.getAction(LabelAction.class)).map(LabelAction::getDisplayName)
        ).filter(Optional::isPresent).map(Optional::get).findFirst();
    }

    @CheckForNull
    static ChecksOutput getOutput(final Run run) {
        if (run instanceof WorkflowRun) {
            FlowExecution execution = ((WorkflowRun) run).getExecution();
            if (execution != null) {
                return getOutput(execution);
            }
        }
        return null;
    }

    @CheckForNull
    @SuppressWarnings({"PMD.ConfusingTernary", "PMD.NPathComplexity", "JavaNCSS"})
    static ChecksOutput getOutput(final FlowExecution execution) {

        FlowGraphTable table = new FlowGraphTable(execution);
        table.build();

        Stack<Integer> indentationStack = new Stack<>();

        StringBuilder summaryBuilder = new StringBuilder();
        StringBuilder textBuilder = new StringBuilder();

        table.getRows().forEach(row -> {
            final FlowNode flowNode = row.getNode();

            boolean isStage = isStage(flowNode);
            boolean isParallel = isParallelBranch(flowNode);
            ErrorAction errorAction = flowNode.getError();
            WarningAction warningAction = flowNode.getPersistentAction(WarningAction.class);

            if (!isStage
                    && !isParallel
                    && errorAction == null
                    && warningAction == null) {
                return;
            }

            if (isStage || isParallel) {
                while (!indentationStack.isEmpty() && row.getTreeDepth() < indentationStack.peek()) {
                    indentationStack.pop();
                }
                if (indentationStack.isEmpty() || row.getTreeDepth() > indentationStack.peek()) {
                    indentationStack.push(row.getTreeDepth());
                }
                textBuilder.append(String.join("", Collections.nCopies(indentationStack.size(), "  ")));
                textBuilder.append("* ");

                final String displayName;
                if (isParallel) {
                    displayName = flowNode.getAction(ThreadNameAction.class).getThreadName();
                }
                else {
                    displayName = flowNode.getDisplayName();
                }
                textBuilder.append(displayName);

                if (flowNode.isActive()) {
                    textBuilder.append(" *(running)*");
                }
                else if (row.getDurationMillis() > 0) {
                    textBuilder.append(String.format(" *(%s)*", row.getDurationString()));
                }
            }
            else {
                List<String> location = flowNode.getEnclosingBlocks().stream()
                        .map(BuildStatusChecksPublisher::getStageOrBranchName)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());

                Collections.reverse(location);

                location.add(flowNode.getDisplayName());

                summaryBuilder.append(String.format("### `%s`%n", String.join(" / ", location)));

                summaryBuilder.append(String.format("%s in `%s` step", errorAction == null ? "Warning" : "Error", flowNode.getDisplayFunctionName()));
                String arguments = ArgumentsAction.getStepArgumentsAsString(flowNode);
                if (arguments != null) {
                    summaryBuilder.append(String.format(", with arguments `%s`.%n", arguments));
                }
                else {
                    summaryBuilder.append(".\n");
                }

                textBuilder.append(String.join("", Collections.nCopies(indentationStack.size() + 1, "  ")));
                if (errorAction != null) {
                    textBuilder.append(String.format("**Error**: *%s*", errorAction.getDisplayName()));
                    summaryBuilder.append(String.format("```%n%s%n```%n<details>%n<summary>Stack trace</summary>%n%n```%n%s%n```%n</details>%n",
                            errorAction.getDisplayName(),
                            ExceptionUtils.getStackTrace(errorAction.getError())));
                }
                else {
                    textBuilder.append(String.format("**Unstable**: *%s*", warningAction.getMessage()));
                    summaryBuilder.append(String.format("```%n%s%n```%n%n", warningAction.getMessage()));
                }
            }

            textBuilder.append("\n");
        });

        return new ChecksOutput.ChecksOutputBuilder()
                .withTitle("Run Summary")
                .withSummary(summaryBuilder.toString())
                .withText(textBuilder.toString())
                .build();
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
            if (!isStage(node) && !isParallelBranch(node)) {
                return;
            }

            Run<?, ?> run;
            try {
                run = (Run) node.getExecution().getOwner().getExecutable();
            }
            catch (IOException e) {
                return;
            }

            getChecksName(run).ifPresent(checksName -> publish(ChecksPublisherFactory.fromRun(run, TaskListener.NULL),
                    ChecksStatus.IN_PROGRESS, ChecksConclusion.NONE, checksName, getOutput(node.getExecution())));

        }
    }
}
