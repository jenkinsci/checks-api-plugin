package io.jenkins.plugins.checks.steps;

import io.jenkins.plugins.checks.api.ChecksAction;
import io.jenkins.plugins.checks.api.ChecksAnnotation;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the pipeline step to publish checks.
 */
class PublishChecksStepITest extends IntegrationTestWithJenkinsPerTest {

    /**
     * Provide a {@link CapturingChecksPublisher} to check published checks on each test.
     */
    @TestExtension
    public static class CapturingChecksPublisherTestExtension extends CapturingChecksPublisher.Factory {
        // activate test extension
    }

    private CapturingChecksPublisher.Factory getFactory() {
        return getJenkins().getInstance().getExtensionList(ChecksPublisherFactory.class)
                .stream()
                .filter(f -> f instanceof CapturingChecksPublisher.Factory)
                .map(f -> (CapturingChecksPublisher.Factory) f)
                .findAny()
                .orElseThrow(() -> new AssertionError("No CapturingChecksPublisher registered as @TestExtension?"));
    }

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
                + "actions: [[label:'test-label', description:'test-desc', identifier:'test-id']], "
                + "annotations: ["
                + "    [path:'Jenkinsfile', startLine:1, endLine:1, message:'test with only required params'], "
                + "    [path:'Jenkinsfile', startLine:2, endLine:2, message:'test with all params', "
                + "        annotationLevel:'NOTICE', startColumn:0, endColumn:10, title:'integration test', "
                + "        rawDetails:'raw details']]"));

        assertThat(JenkinsRule.getLog(buildSuccessfully(job)))
                .contains("[Pipeline] publishChecks");

        assertThat(getFactory().getPublishedChecks().size()).isEqualTo(1);

        ChecksDetails details = getFactory().getPublishedChecks().get(0);

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
        assertThat(output.getChecksAnnotations()).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
                new ChecksAnnotation.ChecksAnnotationBuilder()
                        .withPath("Jenkinsfile")
                        .withStartLine(1)
                        .withEndLine(1)
                        .withAnnotationLevel(ChecksAnnotation.ChecksAnnotationLevel.WARNING)
                        .withMessage("test with only required params")
                        .build(),
                new ChecksAnnotation.ChecksAnnotationBuilder()
                        .withPath("Jenkinsfile")
                        .withStartLine(2)
                        .withEndLine(2)
                        .withAnnotationLevel(ChecksAnnotation.ChecksAnnotationLevel.NOTICE)
                        .withMessage("test with all params")
                        .withStartColumn(0)
                        .withEndColumn(10)
                        .withTitle("integration test")
                        .withRawDetails("raw details")
                        .build());
    }
}
