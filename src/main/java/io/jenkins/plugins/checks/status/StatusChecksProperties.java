package io.jenkins.plugins.checks.status;

import hudson.ExtensionPoint;
import hudson.model.Job;
import hudson.model.Run;

public interface StatusChecksProperties extends ExtensionPoint {
    String getName();
    boolean isActive();
    boolean isApplicable(final Job<?, ?> job);
    boolean isApplicable(final Run<?, ?> run);
}
