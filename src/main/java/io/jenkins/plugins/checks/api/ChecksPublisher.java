package io.jenkins.plugins.checks.api;

import hudson.model.Job;
import hudson.model.TaskListener;
import io.jenkins.plugins.util.PluginLogger;

/**
 * A general publisher for publishing checks to different platforms.
 */
public abstract class ChecksPublisher {
    /**
     * Publishes checks to platforms.
     *
     * @param details
     *         the details of a check
     */
    public abstract void publish(ChecksDetails details);

    /**
     * A null publisher. This publisher will be returned by {@link ChecksPublisherFactory#fromJob(Job, TaskListener)}
     * only when there is no suitable publisher for the given {@code run}.
     */
    public static class NullChecksPublisher extends ChecksPublisher {
        private final PluginLogger logger;

        /**
         * Construct a null checks publisher with {@link PluginLogger}.
         * @param logger
         *         the plugin logger
         */
        public NullChecksPublisher(final PluginLogger logger) {
            super();

            this.logger = logger;
        }

        @Override
        public void publish(final ChecksDetails details) {
            logger.log("No suitable checks publisher found.");
        }
    }
}
