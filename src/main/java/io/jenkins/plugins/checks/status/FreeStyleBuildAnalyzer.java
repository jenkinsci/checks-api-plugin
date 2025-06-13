package io.jenkins.plugins.checks.status;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

import org.apache.commons.lang3.StringUtils;

import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.TruncatedString;

class FreeStyleBuildAnalyzer extends AbstractRunAnalyzer {
    private static final int MAX_LOG_LINES = 1000;

    FreeStyleBuildAnalyzer(final Run<?, ?> run, final boolean suppressLogs) {
        super(run, suppressLogs);
    }

    @Override
    public ChecksOutput extractOutput() {
        String title = extractOutputTitle(Optional.empty());

        ChecksOutput.ChecksOutputBuilder output = new ChecksOutput.ChecksOutputBuilder()
                .withTitle(title);

        if (isSuppressLogs()) {
            return output.build();
        }

        TruncatedString.Builder summaryBuilder = new TruncatedString.Builder()
                .withTruncationText(TRUNCATED_MESSAGE_BUILD_LOG);

        String log;
        try {
            // Get all log lines
            java.util.List<String> allLines = getRun().getLog(MAX_LOG_LINES + 1);
            boolean truncatedLines = allLines.size() > MAX_LOG_LINES;
            int maxMessageSize = MAX_MESSAGE_SIZE_TO_CHECKS_API - LOG_DETAILS_TEMPLATE.length() - 32;
            
            if (truncatedLines) {
                allLines = allLines.subList(1, MAX_LOG_LINES + 1);
            }
            
            log = String.join("\n", allLines);
            log = log.replaceAll("\u001B\\[[;\\d]*m", "");

            TruncatedString.Builder logBuilder = new TruncatedString.Builder()
                    .setChunkOnNewlines()
                    .setTruncateStart()
                    .withTruncationText(TRUNCATED_MESSAGE_BUILD_LOG)
                    .addText(log);
            
            if (truncatedLines) {
                logBuilder.setForceTruncationText();
            }

            log = logBuilder.build().build(maxMessageSize);

            if (StringUtils.isNotBlank(log)) {
                summaryBuilder.addText(String.format(LOG_DETAILS_TEMPLATE, log));
            }
        }
        catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to extract logs for step '%s'",
                    getRun().getDisplayName()).replaceAll("[\r\n]", ""), e);
            return output.build();
        }

        return output.withSummary(summaryBuilder.build()).build();
    }
} 