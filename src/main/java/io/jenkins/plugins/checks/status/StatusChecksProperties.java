package io.jenkins.plugins.checks.status;

import hudson.ExtensionPoint;
import hudson.model.Job;

/**
 * Properties that controls status checks. When no implementations is provided for a job, a {@link
 * DefaultStatusCheckProperties} will be used.
 *
 * @deprecated The interface is incompatible for future changes, use {@link AbstractStatusChecksProperties} instead
 */
@Deprecated
public interface StatusChecksProperties extends ExtensionPoint {
    /**
     * Returns if the implementation is applicable for the {@code job}.
     *
     * @param job
     *         A jenkins job.
     *
     * @return true if applicable
     */
    boolean isApplicable(Job<?, ?> job);

    /**
     * Returns the name of the status check.
     *
     * @param job
     *         A jenkins job.
     *
     * @return the name of the status check
     */
    String getName(Job<?, ?> job);

    /**
     * Returns if skip publishing status checks.
     *
     * @param job
     *         A jenkins job.
     *
     * @return true if skip
     */
    boolean isSkip(Job<?, ?> job);
}
