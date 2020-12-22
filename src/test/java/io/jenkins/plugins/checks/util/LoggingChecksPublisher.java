package io.jenkins.plugins.checks.util;

import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;

import java.util.Optional;

public class LoggingChecksPublisher extends ChecksPublisher {
    @FunctionalInterface
    public interface Formatter {
        String format(ChecksDetails details);
    }

    private Formatter formatter = ChecksDetails::toString;
    private TaskListener listener = TaskListener.NULL;

    @Override
    public void publish(final ChecksDetails details) {
        listener.getLogger().print(formatter.format(details));
    }

    public static class Factory extends ChecksPublisherFactory {
        private final LoggingChecksPublisher publisher = new LoggingChecksPublisher();

        public void setFormatter(final Formatter formatter) {
            publisher.formatter = formatter;
        }

        @Override
        protected Optional<ChecksPublisher> createPublisher(final Run<?, ?> run, final TaskListener listener) {
            publisher.listener = listener;
            return Optional.of(publisher);
        }

        @Override
        protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
            publisher.listener = listener;
            return Optional.of(publisher);
        }
    }
}
