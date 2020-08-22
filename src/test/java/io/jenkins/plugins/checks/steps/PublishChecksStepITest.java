package io.jenkins.plugins.checks.steps;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the pipeline step to publish checks.
 */
public class PublishChecksStepITest extends IntegrationTestWithJenkinsPerTest {
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
                + "text: 'Pipeline support for checks', status: 'IN_PROGRESS', conclusion: 'NONE'"));

        assertThat(JenkinsRule.getLog(buildSuccessfully(job)))
                .contains("[Pipeline] publishChecks")
                .contains("[Checks API] No suitable checks publisher found.");
    }
}
