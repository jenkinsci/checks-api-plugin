package io.jenkins.plugins.checks.api.test;

import hudson.ExtensionList;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link ChecksPublisher} for use in testing, that records each captured checks in a simple list.
 *
 * For example:
 *
 * <pre>
 * public class ChecksPublishingTest {
 *     &#64;TestExtension
 *     public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();
 *
 *     &#64;After
 *     public void clearPublishedChecks() {
 *         PUBLISHER_FACTORY.getPublishedChecks().clear();
 *     }
 * }
 * </pre>
 */
public class CapturingChecksPublisher extends ChecksPublisher {

    private final List<ChecksDetails> publishedChecks = new ArrayList<>();

    @Override
    public void publish(final ChecksDetails details) {
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
    }
}
