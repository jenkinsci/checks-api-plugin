package io.jenkins.plugins.checks.steps;

import io.jenkins.plugins.checks.api.*;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the pipeline step to publish checks.
 */
public class PublishChecksStepITest extends IntegrationTestWithJenkinsPerTest {

    /**
     * Provide a {@link CapturingChecksPublisher} to check published checks on each test.
     */
    @TestExtension
    public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();

    /**
     * Tests that the step "publishChecks" can be used in pipeline script.
     *
     * @throws IOException if fails get log from run
     */
    @Test
    public void shouldPublishChecksWhenUsingPipeline() throws IOException {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("publishChecks name: 'customized-check', "
                + "summary: 'customized check created in pipeline', title: 'Publish Checks Step', "
                + "text: 'Pipeline support for checks', status: 'IN_PROGRESS', conclusion: 'NONE', "
                + "actions: [[label:'test-label', description:'test-desc', identifier:'test-id']]"));

        assertThat(JenkinsRule.getLog(buildSuccessfully(job)))
                .contains("[Pipeline] publishChecks");

        assertThat(PUBLISHER_FACTORY.getPublishedChecks().size()).isEqualTo(1);

        ChecksDetails details = PUBLISHER_FACTORY.getPublishedChecks().get(0);

        assertThat(details.getName()).isPresent().get().isEqualTo("customized-check");
        assertThat(details.getOutput()).isPresent();
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.NONE);
        assertThat(details.getActions()).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
                new ChecksAction("test-label", "test-desc", "test-id"));

        ChecksOutput output = details.getOutput().get();
        assertThat(output.getTitle()).isPresent().get().isEqualTo("Publish Checks Step");
        assertThat(output.getSummary()).isPresent().get().isEqualTo("customized check created in pipeline");
        assertThat(output.getText()).isPresent().get().isEqualTo("Pipeline support for checks");
    }
}
