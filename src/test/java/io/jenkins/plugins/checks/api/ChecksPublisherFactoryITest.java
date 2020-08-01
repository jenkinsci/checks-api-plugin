package io.jenkins.plugins.checks.api;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksPublisher.NullChecksPublisher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Calendar;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests if the {@link ChecksPublisherFactory} produces the right {@link ChecksPublisher} based on the Jenkins context.
 */
public class ChecksPublisherFactoryITest {
    /**
     * A rule which provides a Jenkins instance.
     */
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    /**
     * A {@link NullChecksPublisher} should be returned when creating the {@link ChecksPublisher} for a {@link Run} but
     * no implementation for the checks api is provided.
     *
     * @throws Exception if fails to create freestyle project or build
     */
    @Test
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void shouldReturnNullChecksPublisherForRunWhenNoImplementationIsProvided() throws Exception {
        FreeStyleProject job = rule.createFreeStyleProject();
        FreeStyleBuild run = rule.buildAndAssertSuccess(job);

        ChecksPublisher publisher = ChecksPublisherFactory.fromRun(run);
        assertThat(publisher).isInstanceOf(NullChecksPublisher.class);
    }

    /**
     * A {@link NullChecksPublisher} should be returned when creating the {@link ChecksPublisher} for an
     * {@link Queue.Item} but no implementation for the checks api is provided.
     *
     * @throws Exception if fails to create freestyle project
     */
    @Test
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void shouldReturnNullChecksPublisherForQueueItemWhenNoImplementationIsProvided() throws Exception {
        FreeStyleProject job = rule.createFreeStyleProject();
        Queue.Item item = new Queue.WaitingItem(Calendar.getInstance(), job, Collections.emptyList());

        ChecksPublisher publisher = ChecksPublisherFactory.fromItem(item);
        assertThat(publisher).isInstanceOf(NullChecksPublisher.class);
    }
}
