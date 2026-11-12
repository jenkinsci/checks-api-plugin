package io.jenkins.plugins.checks.status;

import static io.jenkins.plugins.checks.utils.FlowNodeUtils.getEnclosingBlockNames;
import static io.jenkins.plugins.checks.utils.FlowNodeUtils.getEnclosingStagesAndParallels;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.console.AnnotatedLargeText;
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
import java.util.stream.Collectors;
import org.apache.commons.collections.iterators.ReverseListIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
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
class FlowExecutionAnalyzer extends AbstractRunAnalyzer {
    /**
     * The summary keeps at most {@link #MAX_MESSAGE_SIZE_TO_CHECKS_API} bytes from the end of a step log, so only the
     * end of the log is read: a step log can be gigabytes. The first read takes this many bytes from the end.
     */
    static final int MIN_LOG_SIZE_TO_READ = 1024 * 1024;
    /**
     * While the text read is shorter than the summary can hold, the read size doubles, up to this many bytes.
     */
    static final int MAX_LOG_SIZE_TO_READ = 16 * 1024 * 1024;

    private final FlowExecution execution;
    private final Stack<Integer> indentationStack = new Stack<>(); // NOPMD TODO: replace with DeQueue

    FlowExecutionAnalyzer(final Run<?, ?> run, final FlowExecution execution, final boolean suppressLogs) {
        super(run, suppressLogs);
        this.execution = execution;
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
            if (!isSuppressLogs()) {
                // -2 for "\n\n" at the end of the summary and -30 for buffer
                String logTemplate = "<details>%n<summary>Build log</summary>%n%n```%n%s%n```%n</details>";
                int maxMessageSize = MAX_MESSAGE_SIZE_TO_CHECKS_API - nodeSummaryBuilder.length() - logTemplate.length() - 32;
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
        try {
            String logsUrl = String.format("%s%slog", DisplayURLProvider.get().getRoot(), flowNode.getUrl());
            nodeTextBuilder.append(String.format(" - [logs](%s)", logsUrl));
        }
        catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to get log url for step '%s'",
                    flowNode.getDisplayName()).replaceAll("[\r\n]", ""), e);
        }
        nodeTextBuilder.append("\n");
        nodeSummaryBuilder.append("\n\n");  // Ensure a double newline at the end of summary so the subsequence heading works
        return Pair.of(nodeTextBuilder.toString(), nodeSummaryBuilder.toString());
    }

    @Override
    public ChecksOutput extractOutput() {
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
                .withTitle(extractOutputTitle(Optional.ofNullable(title)))
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
        try {
            return getLogTail(logAction.getLogText(), maxMessageSize);
        }
        catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to extract logs for step '%s'",
                    flowNode.getDisplayName()).replaceAll("[\r\n]", ""), e);
            return null;
        }
    }

    /**
     * Returns the end of a log as plain text, at most {@code maxMessageSize} bytes, cut at a line start. Reads only the
     * end of the log: {@link #MIN_LOG_SIZE_TO_READ} bytes, doubled while the text is not longer than
     * {@code maxMessageSize}, up to {@link #MAX_LOG_SIZE_TO_READ} bytes. The result is the same as if the whole log
     * was read, unless the last {@link #MAX_LOG_SIZE_TO_READ} bytes hold less text than {@code maxMessageSize} (for
     * example, mostly console notes): then it has fewer lines.
     *
     * @param logText
     *         the log
     * @param maxMessageSize
     *         the maximum size in bytes of the returned text
     *
     * @return the end of the log, or {@code null} if the log is empty
     * @throws IOException
     *         if the log cannot be read
     */
    @CheckForNull
    static String getLogTail(final AnnotatedLargeText<?> logText, final int maxMessageSize) throws IOException {
        long length = logText.length();
        long size = MIN_LOG_SIZE_TO_READ;
        long start;
        String log;
        do {
            start = Math.max(0, length - size);
            log = readLog(logText, start);
            if (log == null) {
                return null;
            }
            size *= 2;
        }
        while (start > 0 && size <= MAX_LOG_SIZE_TO_READ
                && log.getBytes(StandardCharsets.UTF_8).length <= maxMessageSize);

        TruncatedString.Builder builder = new TruncatedString.Builder()
                .setChunkOnNewlines()
                .setTruncateStart()
                .withTruncationText(TRUNCATED_MESSAGE_BUILD_LOG)
                .addText(log);
        if (start > 0) {
            builder.setForceTruncationText();
        }
        return builder.build().build(maxMessageSize);
    }

    /**
     * Reads a log from {@code start} to its end as plain text. If {@code start > 0}, drops the first line: it starts
     * at an arbitrary byte, maybe inside a character or a console note.
     *
     * @param logText
     *         the log
     * @param start
     *         the byte offset to read from
     *
     * @return the text, or {@code null} if there are no bytes after {@code start}
     * @throws IOException
     *         if the log cannot be read
     */
    @CheckForNull
    private static String readLog(final AnnotatedLargeText<?> logText, final long start) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (logText.writeLogTo(start, out) == start) {
                return null;
            }
            String log = out.toString(StandardCharsets.UTF_8);
            if (start > 0) {
                int firstLineEnd = log.indexOf('\n');
                log = firstLineEnd < 0 ? "" : log.substring(firstLineEnd + 1);
            }
            // strip ansi color codes
            return log.replaceAll("\u001B\\[[;\\d]*m", "");
        }
    }
}
