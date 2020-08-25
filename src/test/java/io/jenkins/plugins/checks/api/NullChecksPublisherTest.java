package io.jenkins.plugins.checks.api;

import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksPublisher.NullChecksPublisher;
import io.jenkins.plugins.util.PluginLogger;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class NullChecksPublisherTest {
    @Test
    void shouldPublishNothingWhenInvokingPublish() {
        PluginLogger logger = mock(PluginLogger.class);
        NullChecksPublisher publisher = new NullChecksPublisher(logger);
        publisher.publish(new ChecksDetailsBuilder().build());

        verify(logger, times(1)).log("No suitable checks publisher found.");
    }
}