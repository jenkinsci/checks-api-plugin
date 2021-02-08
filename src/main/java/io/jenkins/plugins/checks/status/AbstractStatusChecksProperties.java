package io.jenkins.plugins.checks.status;

import hudson.ExtensionPoint;
import hudson.model.Job;

/**
 * Extension points for implementations to provide status checks properties.
 *
 * When no implementations is provided for a job, a {@link DefaultStatusCheckProperties} will be used.
 */
public abstract class AbstractStatusChecksProperties implements ExtensionPoint {
    /**
     * Returns whether the implementation is applicable for the {@code job}.
     *
     * @param job
     *         A jenkins job.
     * @return true if applicable
     */
    public abstract boolean isApplicable(Job<?, ?> job);

    /**
     * Returns the name of the status check.
     *
     * @param job
     *         A jenkins job.
     * @return the name of the status check
     */
    public abstract String getName(Job<?, ?> job);

    /**
     * Returns whether to skip publishing status checks.
     *
     * @param job
     *         A jenkins job.
     * @return true if skip
     */
    public abstract boolean isSkipped(Job<?, ?> job);

    /**
     * Whether to conclude an unstable build as {@link io.jenkins.plugins.checks.api.ChecksConclusion#NEUTRAL},
     * else it would be concluded as {@link io.jenkins.plugins.checks.api.ChecksConclusion#FAILURE};
     * the default is false.
     *
     * @param job
     *         A jenkins job.
     * @return false to treat a unstable build as failure.
     */
    public boolean isUnstableBuildNeutral(Job<?, ?> job) {
        return false;
    }
}

class DefaultStatusCheckProperties extends AbstractStatusChecksProperties {
    @Override
    public boolean isApplicable(final Job<?, ?> job) {
        return false;
    }

    @Override
    public String getName(final Job<?, ?> job) {
        return "Jenkins";
    }

    @Override
    public boolean isSkipped(final Job<?, ?> job) {
        return true;
    }
}
