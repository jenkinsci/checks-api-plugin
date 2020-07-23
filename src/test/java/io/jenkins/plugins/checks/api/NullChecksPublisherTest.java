package io.jenkins.plugins.checks.api;

import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksPublisher.NullChecksPublisher;
import org.junit.jupiter.api.Test;

class NullChecksPublisherTest {
    @Test
    void shouldPublishNothingWhenInvokingPublish() {
        NullChecksPublisher publisher = new NullChecksPublisher();
        publisher.publish(new ChecksDetailsBuilder().build());
    }
}