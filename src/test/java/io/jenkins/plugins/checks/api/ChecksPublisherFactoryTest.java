package io.jenkins.plugins.checks.api;

import hudson.model.Job;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksPublisher.NullChecksPublisher;
import io.jenkins.plugins.util.JenkinsFacade;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChecksPublisherFactoryTest {
    @Test
    void shouldReturnNullChecksPublisherForJobWhenNoImplementationIsProvided() {
        Job<?, ?> job = mock(Job.class);
        TaskListener listener = mock(TaskListener.class);
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);

        when(jenkinsFacade.getExtensionsFor(ChecksPublisherFactory.class))
                .thenReturn(Collections.emptyList());

        assertThat(ChecksPublisherFactory.fromJob(job, listener, jenkinsFacade))
                .isInstanceOf(NullChecksPublisher.class);
    }

    @Test
    void shouldReturnChecksPublisherForJobWhenImplementationIsProvided() {
        Job<?, ?> job = mock(Job.class);
        TaskListener listener = mock(TaskListener.class);
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);

        when(jenkinsFacade.getExtensionsFor(ChecksPublisherFactory.class))
                .thenReturn(Collections.singletonList(new ChecksPublisherFactoryImpl()));

        assertThat(ChecksPublisherFactory.fromJob(job, listener, jenkinsFacade))
                .isInstanceOf(ChecksPublisherImpl.class);
    }

    private static class ChecksPublisherFactoryImpl extends ChecksPublisherFactory {
        @Override
        protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
            return Optional.of(new ChecksPublisherImpl());
        }
    }

    private static class ChecksPublisherImpl extends ChecksPublisher {
        @Override
        public void publish(final ChecksDetails details) {
        }
    }
}
