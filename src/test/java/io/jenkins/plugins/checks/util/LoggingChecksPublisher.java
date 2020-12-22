package io.jenkins.plugins.checks.util;

import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;

import java.util.Optional;

/**
 * Implementation of {@link ChecksPublisher} for use in testing, that logs the checks details in user specified format.
 *
 * For example:
 *
 * <pre>
 * public class ChecksPublishingTest extends IntegrationTestWithJenkinsPerTest {
 *
 *     &#64;TestExtension
 *     public static final LoggingChecksPublisher.Factory PUBLISHER_FACTORY = new LoggingChecksPublisher.Factory();
 *
 *     &#64;Test
 *     public void shouldLogChecks() {
 *         // ...Run a test job...
 *         Run<?, ?> run = buildSuccessfully(createFreeStyleProject());
 *
 *         // ...Inspect logs...
 *         assertThat(JenkinsRule.getLog(run))
 *                 .contains("...")
 *                 .doesNotContain("...");
 *     }
 * }
 * </pre>
 *
 * An example of this can be found in {@link io.jenkins.plugins.checks.status.BuildStatusChecksPublisherITest}
 */
public class LoggingChecksPublisher extends ChecksPublisher {
    private Formatter formatter = ChecksDetails::toString;
    private TaskListener listener = TaskListener.NULL;

    /**
     * Logs the {@code details} using the {@link TaskListener}.
     *
     * @param details
     *         checks details that will be logged
     */
    @Override
    public void publish(final ChecksDetails details) {
        listener.getLogger().print(formatter.format(details));
    }

    /**
     * Implementation of {@link ChecksPublisherFactory} that returns a {@link LoggingChecksPublisher}.
     */
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

    /**
     * Defines how to format a {@link ChecksDetails} to {@link String}.
     */
    @FunctionalInterface
    public interface Formatter {
        /**
         * Formats the {@code details}.
         *
         * @param details
         *         details to format.
         * @return formatted string
         */
        String format(ChecksDetails details);
    }
}
