package io.jenkins.plugins.checks.status;

import java.util.logging.Logger;

import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksOutput;

/**
 * Base class for build analyzers that extract output from Jenkins builds.
 */
abstract class AbstractBuildAnalyzer {
    protected static final Logger LOGGER = Logger.getLogger(AbstractBuildAnalyzer.class.getName());
    protected static final String TRUNCATED_MESSAGE = "\n\nOutput truncated.";
    protected static final String TRUNCATED_MESSAGE_BUILD_LOG = "Build log truncated.\n\n";
    protected static final int MAX_MESSAGE_SIZE_TO_CHECKS_API = 65_535;
    protected static final String LOG_DETAILS_TEMPLATE = "<details><summary>Build Log</summary>%n%n```%n%s%n```%n%n</details>";

    private final Run<?, ?> run;
    private final boolean suppressLogs;

    protected AbstractBuildAnalyzer(final Run<?, ?> run, final boolean suppressLogs) {
        this.run = run;
        this.suppressLogs = suppressLogs;
    }

    /**
     * Gets the run associated with this analyzer.
     *
     * @return the run
     */
    protected Run<?, ?> getRun() {
        return run;
    }

    /**
     * Checks if logs should be suppressed.
     *
     * @return true if logs should be suppressed
     */
    protected boolean isSuppressLogs() {
        return suppressLogs;
    }

    /**
     * Extracts output from the build.
     *
     * @return the extracted output
     */
    public abstract ChecksOutput extractOutput();

    /**
     * Extract the output title based on the build result.
     *
     * @param title
     *         custom title to use if the build failed
     * @return the output title
     */
    protected String extractOutputTitle(final String title) {
        Result result = getRun().getResult();
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