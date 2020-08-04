package io.jenkins.plugins.checks.api;

import hudson.model.Job;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A general publisher for publishing checks to different platforms.
 */
@Restricted(Beta.class)
public abstract class ChecksPublisher {
    /**
     * Publishes checks to platforms.
     *
     * @param details
     *         the details of a check
     */
    public abstract void publish(ChecksDetails details);

    /**
     * A null publisher. This publisher will be returned by
     * {@link ChecksPublisherFactory#fromJob(Job, TaskListener)} only when there is no suitable publisher for the given {@code run}.
     */
    public static class NullChecksPublisher extends ChecksPublisher {
        @Override
        public void publish(final ChecksDetails details) { }
    }
}
