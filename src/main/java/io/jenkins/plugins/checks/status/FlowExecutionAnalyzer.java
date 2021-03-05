package io.jenkins.plugins.checks.status;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.TruncatedString;
import org.apache.commons.collections.iterators.ReverseListIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class FlowExecutionAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(FlowExecutionAnalyzer.class.getName());
    private static final String TRUNCATED_MESSAGE = "\n\nOutput truncated.";

    private final Run<?, ?> run;
    private final FlowExecution execution;
    private final Stack<Integer> indentationStack = new Stack<>();
    private final boolean suppressLogs;

    FlowExecutionAnalyzer(final Run<?, ?> run, final FlowExecution execution, final boolean suppressLogs) {
        this.run = run;
        this.execution = execution;
        this.suppressLogs = suppressLogs;
    }

    private static Optional<String> getStageOrBranchName(final FlowNode node) {
        return getParallelName(node)
                .map(Optional::of)
                .orElse(getStageName(node));
    }

    private static Optional<String> getStageName(final FlowNode node) {
        return Optional.ofNullable(node)
                .filter(n -> n.getAction(ThreadNameAction.class) == null)
                .map(n -> n.getAction(LabelAction.class))
                .map(LabelAction::getDisplayName);
    }

    private static Optional<String> getParallelName(final FlowNode node) {
        return Optional.ofNullable(node)
                .filter(n -> n.getAction(LabelAction.class) != null)
                .map(n -> n.getAction(ThreadNameAction.class))
                .map(ThreadNameAction::getThreadName);
    }

    private Pair<String, String> processStageOrBranchRow(final FlowGraphTable.Row row,
                                                         final String stageOrBranchName) {
        final StringBuilder nodeTextBuilder = new StringBuilder();
        while (!indentationStack.isEmpty() && row.getTreeDepth() < indentationStack.peek()) {
            indentationStack.pop();
        }
        if (indentationStack.isEmpty() || row.getTreeDepth() > indentationStack.peek()) {
            indentationStack.push(row.getTreeDepth());
        }
        nodeTextBuilder.append(String.join("", Collections.nCopies(indentationStack.size(), "  ")));
        nodeTextBuilder.append("* ");

        nodeTextBuilder.append(stageOrBranchName);

        if (row.getNode().isActive()) {
            nodeTextBuilder.append(" *(running)*");
        }
        else if (row.getDurationMillis() > 0) {
            nodeTextBuilder.append(String.format(" *(%s)*", row.getDurationString()));
        }
        nodeTextBuilder.append("\n");
        return Pair.of(nodeTextBuilder.toString(), "");
    }

    private Pair<String, String> processErrorOrWarningRow(final FlowGraphTable.Row row, final ErrorAction errorAction,
                                                          final WarningAction warningAction) {
        FlowNode flowNode = row.getNode();

        StringBuilder nodeSummaryBuilder = new StringBuilder();
        StringBuilder nodeTextBuilder = new StringBuilder();

        List<String> location = flowNode.getEnclosingBlocks().stream()
                .map(FlowExecutionAnalyzer::getStageOrBranchName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        Collections.reverse(location);

        location.add(flowNode.getDisplayName());

        nodeSummaryBuilder.append(String.format("### `%s`%n", String.join(" / ", location)));

        nodeSummaryBuilder.append(String.format("%s in `%s` step", errorAction == null ? "Warning" : "Error",
                flowNode.getDisplayFunctionName()));
        String arguments = ArgumentsAction.getStepArgumentsAsString(flowNode);
        if (arguments == null) {
            nodeSummaryBuilder.append(".\n");
        }
        else {
            nodeSummaryBuilder.append(String.format(", with arguments `%s`.%n", arguments));
        }

        nodeTextBuilder.append(String.join("", Collections.nCopies(indentationStack.size() + 1, "  ")));
        if (warningAction == null) {
            nodeTextBuilder.append(String.format("**Error**: *%s*", errorAction.getDisplayName()));
            nodeSummaryBuilder.append(String.format("```%n%s%n```%n", errorAction.getDisplayName()));
            if (!suppressLogs) {
                String log = getLog(flowNode);
                if (StringUtils.isNotBlank(log)) {
                    nodeSummaryBuilder.append(String.format("<details>%n<summary>Build log</summary>%n%n```%n%s%n```%n</details>", log));
                }
            }
        }
        else {
            nodeTextBuilder.append(String.format("**Unstable**: *%s*", warningAction.getMessage()));
            nodeSummaryBuilder.append(String.format("```%n%s%n```", warningAction.getMessage()));
        }
        nodeTextBuilder.append("\n");
        nodeSummaryBuilder.append("\n\n");  // Ensure a double newline at the end of summary so the subsequence heading works
        return Pair.of(nodeTextBuilder.toString(), nodeSummaryBuilder.toString());
    }

    ChecksOutput extractOutput() {
        FlowGraphTable table = new FlowGraphTable(execution);
        table.build();

        TruncatedString.Builder summaryBuilder = new TruncatedString.Builder()
                .withTruncationText(TRUNCATED_MESSAGE);
        TruncatedString.Builder textBuilder = new TruncatedString.Builder()
                .withTruncationText(TRUNCATED_MESSAGE);
        indentationStack.clear();

        String title = null;
        for (FlowGraphTable.Row row : table.getRows()) {
            final FlowNode flowNode = row.getNode();

            Optional<String> stageOrBranchName = getStageOrBranchName(flowNode);
            ErrorAction errorAction = flowNode.getError();
            WarningAction warningAction = flowNode.getPersistentAction(WarningAction.class);

            if (stageOrBranchName.isPresent() || errorAction != null || warningAction != null) {
                final Pair<String, String> nodeInfo = stageOrBranchName.map(s -> processStageOrBranchRow(row, s))
                        .orElseGet(() -> processErrorOrWarningRow(row, errorAction, warningAction));

                // the last title will be used in the ChecksOutput (if any are found)
                if (!stageOrBranchName.isPresent()) {
                    title = getPotentialTitle(flowNode, errorAction);
                }

                textBuilder.addText(nodeInfo.getLeft());
                summaryBuilder.addText(nodeInfo.getRight());
            }
        }

        return new ChecksOutput.ChecksOutputBuilder()
                .withTitle(extractOutputTitle(title))
                .withSummary(summaryBuilder.build())
                .withText(textBuilder.build())
                .build();
    }

    private String getPotentialTitle(final FlowNode flowNode, final ErrorAction errorAction) {
        final String whereBuildFailed = String.format("%s in '%s' step", errorAction == null ? "warning" : "error",
                flowNode.getDisplayFunctionName());

        List<FlowNode> enclosingStagesAndParallels = getEnclosingStagesAndParallels(flowNode);
        List<String> enclosingBlockNames = getEnclosingBlockNames(enclosingStagesAndParallels);

        return StringUtils.join(new ReverseListIterator(enclosingBlockNames), "/") + ": " + whereBuildFailed;
    }

    private static boolean isStageNode(@NonNull final FlowNode node) {
        if (node instanceof StepNode) {
            StepDescriptor d = ((StepNode) node).getDescriptor();
            return d != null && d.getFunctionName().equals("stage");
        }
        else {
            return false;
        }
    }

    /**
     * Get the stage and parallel branch start node IDs (not the body nodes) for this node, innermost first.
     * @param node A flownode.
     * @return A nonnull, possibly empty list of stage/parallel branch start nodes, innermost first.
     */
    @NonNull
    private static List<FlowNode> getEnclosingStagesAndParallels(final FlowNode node) {
        List<FlowNode> enclosingBlocks = new ArrayList<>();
        for (FlowNode enclosing : node.getEnclosingBlocks()) {
            if (enclosing != null && enclosing.getAction(LabelAction.class) != null
                    && (isStageNode(enclosing) || enclosing.getAction(ThreadNameAction.class) != null)) {
                enclosingBlocks.add(enclosing);
            }
        }

        return enclosingBlocks;
    }

    @NonNull
    private static List<String> getEnclosingBlockNames(@NonNull final List<FlowNode> nodes) {
        List<String> names = new ArrayList<>();
        for (FlowNode n : nodes) {
            ThreadNameAction threadNameAction = n.getPersistentAction(ThreadNameAction.class);
            LabelAction labelAction = n.getPersistentAction(LabelAction.class);
            if (threadNameAction != null) {
                // If we're on a parallel branch with the same name as the previous (inner) node, that generally
                // means we're in a Declarative parallel stages situation, so don't add the redundant branch name.
                if (names.isEmpty() || !threadNameAction.getThreadName().equals(names.get(names.size() - 1))) {
                    names.add(threadNameAction.getThreadName());
                }
            }
            else if (labelAction != null) {
                names.add(labelAction.getDisplayName());
            }
        }
        return names;
    }

    @CheckForNull
    private static String getLog(final FlowNode flowNode) {
        LogAction logAction = flowNode.getAction(LogAction.class);
        if (logAction == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (logAction.getLogText().writeLogTo(0, out) == 0) {
                return null;
            }

            String outputString = out.toString(StandardCharsets.UTF_8.toString());
            // strip ansi color codes
            return outputString.replaceAll("\u001B\\[[;\\d]*m", "");
        }
        catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to extract logs for step '%s'",
                    flowNode.getDisplayName()).replaceAll("[\r\n]", ""), e);
            return null;
        }
    }

    private String extractOutputTitle(final String title) {
        Result result = run.getResult();
        if (result == null) {
            return "In progress";
        }
        if (result.isBetterOrEqualTo(Result.SUCCESS)) {
            return "Success";
        }

        if (title != null) {
            return title;
        }

        if (result.isBetterOrEqualTo(Result.UNSTABLE)) {
            return "Unstable";
        }
        if (result.isBetterOrEqualTo(Result.FAILURE)) {
            return "Failure";
        }
        if (result.isBetterOrEqualTo(Result.NOT_BUILT)) {
            return "Skipped";
        }
        if (result.isBetterOrEqualTo(Result.ABORTED)) {
            return "Aborted";
        }
        throw new IllegalStateException("Unsupported run result: " + result);
    }
}
