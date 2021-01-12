package io.jenkins.plugins.checks.status;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.TruncatedString;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;

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

class FlowExecutionAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(FlowExecutionAnalyzer.class.getName());

    private static final String TRUNCATED_MESSAGE = "\n\nOutput truncated.";

    private final Run<?, ?> run;
    private final FlowExecution execution;

    FlowExecutionAnalyzer(final Run<?, ?> run, final FlowExecution execution) {
        this.run = run;
        this.execution = execution;
    }

    private static Optional<String> getStageOrBranchName(final FlowNode node) {
        return getParallelName(node)
                .map(Optional::of)
                .orElse(getStageName(node));
    }

    private static Optional<String> getStageName(final FlowNode node) {
        return Optional.ofNullable(node)
                .filter(n -> n.getAction(ThreadNameAction.class) == null)
                .flatMap(n -> Optional.ofNullable(n.getAction(LabelAction.class)))
                .map(LabelAction::getDisplayName);
    }

    private static Optional<String> getParallelName(final FlowNode node) {
        return Optional.ofNullable(node)
                .filter(n -> n.getAction(LabelAction.class) != null)
                .flatMap(n -> Optional.ofNullable(n.getAction(ThreadNameAction.class)))
                .map(ThreadNameAction::getThreadName);
    }

    @SuppressWarnings("JavaNCSS")
    ChecksOutput extractOutput() {

        FlowGraphTable table = new FlowGraphTable(execution);
        table.build();

        Stack<Integer> indentationStack = new Stack<>();

        TruncatedString.Builder summaryBuilder = new TruncatedString.Builder()
                .withTruncationText(TRUNCATED_MESSAGE);
        TruncatedString.Builder textBuilder = new TruncatedString.Builder()
                .withTruncationText(TRUNCATED_MESSAGE);

        table.getRows().forEach(row -> {
            final FlowNode flowNode = row.getNode();

            Optional<String> stageOrBranchName = getStageOrBranchName(flowNode);
            ErrorAction errorAction = flowNode.getError();
            WarningAction warningAction = flowNode.getPersistentAction(WarningAction.class);

            if (!stageOrBranchName.isPresent()
                    && errorAction == null
                    && warningAction == null) {
                return;
            }

            StringBuilder nodeSummaryBuilder = new StringBuilder();
            StringBuilder nodeTextBuilder = new StringBuilder();

            if (stageOrBranchName.isPresent()) {
                while (!indentationStack.isEmpty() && row.getTreeDepth() < indentationStack.peek()) {
                    indentationStack.pop();
                }
                if (indentationStack.isEmpty() || row.getTreeDepth() > indentationStack.peek()) {
                    indentationStack.push(row.getTreeDepth());
                }
                nodeTextBuilder.append(String.join("", Collections.nCopies(indentationStack.size(), "  ")));
                nodeTextBuilder.append("* ");

                nodeTextBuilder.append(stageOrBranchName.get());

                if (flowNode.isActive()) {
                    nodeTextBuilder.append(" *(running)*");
                }
                else if (row.getDurationMillis() > 0) {
                    nodeTextBuilder.append(String.format(" *(%s)*", row.getDurationString()));
                }
            }
            else {
                List<String> location = flowNode.getEnclosingBlocks().stream()
                        .map(FlowExecutionAnalyzer::getStageOrBranchName)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());

                Collections.reverse(location);

                location.add(flowNode.getDisplayName());

                nodeSummaryBuilder.append(String.format("### `%s`%n", String.join(" / ", location)));

                nodeSummaryBuilder.append(String.format("%s in `%s` step", errorAction == null ? "Warning" : "Error", flowNode.getDisplayFunctionName()));
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
                    String log = getLog(flowNode);
                    if (StringUtils.isNotBlank(log)) {
                        nodeSummaryBuilder.append(String.format("```%n%s%n```%n<details>%n<summary>Build log</summary>%n```%n%s```%n</details>%n",
                                errorAction.getDisplayName(),
                                log));
                    }
                }
                else {
                    nodeTextBuilder.append(String.format("**Unstable**: *%s*", warningAction.getMessage()));
                    nodeSummaryBuilder.append(String.format("```%n%s%n```%n%n", warningAction.getMessage()));
                }
            }

            nodeTextBuilder.append("\n");

            summaryBuilder.addText(nodeSummaryBuilder.toString());
            textBuilder.addText(nodeTextBuilder.toString());
        });

        return new ChecksOutput.ChecksOutputBuilder()
                .withTitle(extractOutputTitle())
                .withSummary(summaryBuilder.build())
                .withText(textBuilder.build())
                .build();
    }

    @CheckForNull
    @SuppressFBWarnings("CRLF_INJECTION_LOGS")
    private static String getLog(final FlowNode flowNode) {
        LogAction logAction = flowNode.getAction(LogAction.class);
        if (logAction == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (logAction.getLogText().writeLogTo(0, out) == 0) {
                return null;
            }
            return out.toString(StandardCharsets.UTF_8.toString());
        }
        catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to extract logs for step '%s'", flowNode.getDisplayName()), e);
            return null;
        }
    }

    private String extractOutputTitle() {
        Result result = run.getResult();
        if (result == null) {
            return "In progress";
        }
        if (result.isBetterOrEqualTo(Result.SUCCESS)) {
            return "Success";
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
