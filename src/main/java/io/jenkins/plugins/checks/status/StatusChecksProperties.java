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
    boolean isApplicable(final Job<?, ?> job);

    /**
     * Returns the name of the status check.
     *
     * @return the name of the status check
     */
    String getName();

    /**
     * Returns if skip publishing status checks.
     *
     * @return true if skip
     */
    boolean isSkipped();
}
