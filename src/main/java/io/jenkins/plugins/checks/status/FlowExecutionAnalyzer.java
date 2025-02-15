package io.jenkins.plugins.checks.status;

import static io.jenkins.plugins.checks.utils.FlowNodeUtils.getEnclosingBlockNames;
import static io.jenkins.plugins.checks.utils.FlowNodeUtils.getEnclosingStagesAndParallels;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.TruncatedString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.collections.iterators.ReverseListIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

@SuppressWarnings("PMD.GodClass")
class FlowExecutionAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(FlowExecutionAnalyzer.class.getName());
    private static final String TRUNCATED_MESSAGE = "\n\nOutput truncated.";
    private static final String TRUNCATED_MESSAGE_BUILD_LOG = "Build log truncated.\n\n";
    private static final int MAX_MESSAGE_SIZE_TO_CHECKS_API = 65_535;

    private final Run<?, ?> run;
    private final FlowExecution execution;
    private final Stack<Integer> indentationStack = new Stack<>(); // NOPMD TODO: replace with DeQueue
    private final boolean suppressLogs;

    FlowExecutionAnalyzer(final Run<?, ?> run, final FlowExecution execution, final boolean suppressLogs) {
        this.run = run;
        this.execution = execution;
        this.suppressLogs = suppressLogs;
    }

    private static Optional<String> getStageOrBranchName(final FlowNode node) {
        if (node instanceof BlockStartNode) {
            // a stage or parallel branch must be a BlockStartNode
            return getParallelName(node).or(() -> getStageName(node));
        }
        else {
            // otherwise, this is a regular step, don't return a name
            return Optional.empty();
        }
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
        else {
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
            var displayName = errorAction == null ? "[no error action]" : errorAction.getDisplayName();
            nodeTextBuilder.append(String.format("**Error**: *%s*", displayName));
            nodeSummaryBuilder.append(String.format("```%n%s%n```%n", displayName));
            if (!suppressLogs) {
                // -2 for "\n\n" at the end of the summary
                String logTemplate = "<details>%n<summary>Build log</summary>%n%n```%n%s%n```%n</details>";
                int maxMessageSize = MAX_MESSAGE_SIZE_TO_CHECKS_API - nodeSummaryBuilder.length() - logTemplate.length() - 2;
                String log = getLog(flowNode, maxMessageSize);
                if (StringUtils.isNotBlank(log)) {
                    nodeSummaryBuilder.append(String.format(logTemplate, log));
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
                if (stageOrBranchName.isEmpty()) {
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

    @CheckForNull
    private static String getLog(final FlowNode flowNode, final int maxMessageSize) {
        LogAction logAction = flowNode.getAction(LogAction.class);
        if (logAction == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (logAction.getLogText().writeLogTo(0, out) == 0) {
                return null;
            }

            String outputString = out.toString(StandardCharsets.UTF_8);
            // strip ansi color codes
            String log = outputString.replaceAll("\u001B\\[[;\\d]*m", "");

            return new TruncatedString.Builder()
                    .setChunkOnNewlines()
                    .setTruncateStart()
                    .withTruncationText(TRUNCATED_MESSAGE_BUILD_LOG)
                    .addText(log)
                    .build()
                    .build(maxMessageSize);
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
