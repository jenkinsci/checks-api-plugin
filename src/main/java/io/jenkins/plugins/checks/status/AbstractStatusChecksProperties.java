package io.jenkins.plugins.checks.status;

import hudson.ExtensionPoint;
import hudson.model.Job;

/**
 * Extension points for implementations to provide status checks properties.
 *
 * When no implementations is provided for a job, a {@link DefaultStatusCheckProperties} will be used.
 */
public abstract class AbstractStatusChecksProperties implements StatusChecksProperties, ExtensionPoint {
    @Override
    public abstract boolean isApplicable(Job<?, ?> job);

    @Override
    public abstract String getName(Job<?, ?> job);

    @Override
    public abstract boolean isSkip(Job<?, ?> job);
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
    public boolean isSkip(final Job<?, ?> job) {
        return true;
    }
}
