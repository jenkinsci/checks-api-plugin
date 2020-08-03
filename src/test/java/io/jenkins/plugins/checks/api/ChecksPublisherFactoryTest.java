package io.jenkins.plugins.checks.api;

import hudson.model.Job;
import hudson.model.Run;
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
    void shouldReturnNullChecksPublisherForRunWhenNoImplementationIsProvided() {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);

        assertThat(ChecksPublisherFactory.fromRun(run, listener,
                createJenkinsFacadeWithNoChecksPublisherFactoryImplementation()))
                .isInstanceOf(NullChecksPublisher.class);
    }

    @Test
    void shouldReturnChecksPublisherForRunWhenImplementationIsProvided() {
        Run<?, ?> run = mock(Run.class);
        TaskListener listener = mock(TaskListener.class);

        assertThat(ChecksPublisherFactory.fromRun(run, listener,
                createJenkinsFacadeWithChecksPublisherFactoryImplementation()))
                .isInstanceOf(ChecksPublisherImpl.class);
    }

    @Test
    void shouldReturnNullChecksPublisherForJobWhenNoImplementationIsProvided() {
        Job<?, ?> job = mock(Job.class);
        TaskListener listener = mock(TaskListener.class);

        assertThat(ChecksPublisherFactory.fromJob(job, listener,
                createJenkinsFacadeWithNoChecksPublisherFactoryImplementation()))
                .isInstanceOf(NullChecksPublisher.class);
    }

    @Test
    void shouldReturnChecksPublisherForJobWhenImplementationIsProvided() {
        Job<?, ?> job = mock(Job.class);
        TaskListener listener = mock(TaskListener.class);

        assertThat(ChecksPublisherFactory.fromJob(job, listener,
                createJenkinsFacadeWithChecksPublisherFactoryImplementation()))
                .isInstanceOf(ChecksPublisherImpl.class);
    }

    private JenkinsFacade createJenkinsFacadeWithNoChecksPublisherFactoryImplementation() {
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);

        when(jenkinsFacade.getExtensionsFor(ChecksPublisherFactory.class))
                .thenReturn(Collections.emptyList());

        return jenkinsFacade;
    }

    private JenkinsFacade createJenkinsFacadeWithChecksPublisherFactoryImplementation() {
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);

        when(jenkinsFacade.getExtensionsFor(ChecksPublisherFactory.class))
                .thenReturn(Collections.singletonList(new ChecksPublisherFactoryImpl()));

        return jenkinsFacade;
    }

    private static class ChecksPublisherFactoryImpl extends ChecksPublisherFactory {
        @Override
        protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
            return Optional.of(new ChecksPublisherImpl());
        }

        @Override
        protected Optional<ChecksPublisher> createPublisher(final Run<?, ?> run, final TaskListener listener) {
            return Optional.of(new ChecksPublisherImpl());
        }
    }

    private static class ChecksPublisherImpl extends ChecksPublisher {
        @Override
        public void publish(final ChecksDetails details) {
        }
    }
}
