package io.jenkins.plugins.checks.util;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import hudson.ExtensionList;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;

import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;

/**
 * Implementation of {@link ChecksPublisher} for use in testing, that records each captured checks in a simple list.
 *
 * <p>
 * For example:
 * </p>
 * <pre>
 * public class ChecksPublishingTest extends IntegrationTestWithJenkinsPerTest {
 *
 *     &#64;TestExtension
 *     public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();
 *
 *     &#64;After
 *     public void clearPublishedChecks() {
 *         PUBLISHER_FACTORY.getPublishedChecks().clear();
 *     }
 *
 *     &#64;Test
 *     public void testChecksPublishing() {
 *
 *         // ...Run a test job...
 *
 *         List&lt;ChecksDetails&gt; publishedDetails = PUBLISHER_FACTORY.getPublishedChecks();
 *
 *         // ...Inspect published checks...
 *     }
 * }
 * </pre>
 *
 * <p>
 * An example of this can be found in {@link io.jenkins.plugins.checks.steps.PublishChecksStepITest}
 * </p>
 */
public class CapturingChecksPublisher extends ChecksPublisher {
    // Checks may be published from several threads
    private final List<ChecksDetails> publishedChecks = new CopyOnWriteArrayList<>();
    private volatile long inProgressDelayMillis;

    @Override
    public void publish(final ChecksDetails details) {
        if (details.getStatus() == ChecksStatus.IN_PROGRESS && inProgressDelayMillis > 0) {
            try {
                Thread.sleep(inProgressDelayMillis);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        publishedChecks.add(details);
    }

    /**
     * Implementation of {@link ChecksPublisherFactory} that returns a {@link CapturingChecksPublisher}.
     */
    public static class Factory extends ChecksPublisherFactory {
        private final CapturingChecksPublisher publisher = new CapturingChecksPublisher();

        @Override
        protected Optional<ChecksPublisher> createPublisher(final Run<?, ?> run, final TaskListener listener) {
            return Optional.of(publisher);
        }

        @Override
        protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
            return Optional.of(publisher);
        }

        public List<ChecksDetails> getPublishedChecks() {
            return ExtensionList.lookup(Factory.class).get(0).publisher.publishedChecks;
        }

        /**
         * Makes each {@code IN_PROGRESS} publish take the given time, to simulate a slow SCM API.
         *
         * @param millis
         *         the delay in milliseconds, or 0 for none
         */
        public void setInProgressDelayMillis(final long millis) {
            ExtensionList.lookup(Factory.class).get(0).publisher.inProgressDelayMillis = millis;
        }
    }
}
