package io.jenkins.plugins.checks.status;

import hudson.ExtensionPoint;
import hudson.model.Job;

/**
 * Properties that controls status checks.
 *
 * When no implementations is provided for a job, a {@link DefaultStatusCheckProperties} will be used.
 */
public interface StatusChecksProperties extends ExtensionPoint {
    /**
     * Returns if the implementation is applicable for the {@code job}.
     *
     * @param job
     *         A jenkins job.
     * @return true if applicable
     */
    boolean isApplicable(Job<?, ?> job);

    /**
     * Returns the name of the status check.
     *
     * @param job
     *         A jenkins job.
     * @return the name of the status check
     */
    String getName(Job<?, ?> job);

    /**
     * Returns if skip publishing status checks.
     *
     * @param job
     *         A jenkins job.
     * @return true if skip
     */
    boolean isSkip(Job<?, ?> job);

    /**
     * Returns true if unsable builds result in NEUTRAL conclusion, else it will result in FAILURE. Default is false.
     * @param job
     *         A jenkins job.
     * @return true if UNSTABLE should be treated as a NEUTRAL.
     */
    boolean isUnstableNeutral(Job<?, ?> job);
}

class DefaultStatusCheckProperties implements StatusChecksProperties {
    @Override
    public boolean isApplicable(final Job<?, ?> job) {
        return false;
    }

    @Override
    public String getName(final Job<?, ?> job) {
        return "Jenkins";
    }

    @Override
    public boolean isSkip(final Job<?, ?> job) {
        return true;
    }

    @Override
    public boolean isUnstableNeutral(final Job<?, ?> job) {
        return false;
    }
}
