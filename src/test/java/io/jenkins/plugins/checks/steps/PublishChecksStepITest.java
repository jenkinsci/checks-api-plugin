package io.jenkins.plugins.checks.steps;

import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.api.test.CapturingChecksPublisher;
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
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void shouldPublishChecksWhenUsingPipeline() throws IOException {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("publishChecks name: 'customized-check', "
                + "summary: 'customized check created in pipeline', title: 'Publish Checks Step', "
                + "text: 'Pipeline support for checks', status: 'IN_PROGRESS', conclusion: 'NONE'"));

        assertThat(JenkinsRule.getLog(buildSuccessfully(job)))
                .contains("[Pipeline] publishChecks");

        assertThat(PUBLISHER_FACTORY.getPublishedChecks().size()).isEqualTo(1);

        ChecksDetails details = PUBLISHER_FACTORY.getPublishedChecks().get(0);

        assertThat(details.getName().get()).isEqualTo("customized-check");
        assertThat(details.getOutput().get().getTitle().get()).isEqualTo("Publish Checks Step");
        assertThat(details.getOutput().get().getSummary().get()).isEqualTo("customized check created in pipeline");
        assertThat(details.getOutput().get().getText().get()).isEqualTo("Pipeline support for checks");
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.NONE);
    }
}
