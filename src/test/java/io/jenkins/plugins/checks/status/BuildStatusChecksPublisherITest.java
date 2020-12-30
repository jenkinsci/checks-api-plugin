package io.jenkins.plugins.checks.status;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the {@link BuildStatusChecksPublisher} listens to the status of a {@link Run} and publishes status
 * accordingly.
 */
@SuppressWarnings("PMD.AddEmptyString")
public class BuildStatusChecksPublisherITest extends IntegrationTestWithJenkinsPerTest {

    /**
     * Provide a {@link io.jenkins.plugins.checks.util.CapturingChecksPublisher} to capture details.
     */
    @TestExtension
    public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();

    /**
     * Clean captured checks between tests.
     */
    @After
    public void clearChecks() {
        PUBLISHER_FACTORY.getPublishedChecks().clear();
    }

    /**
     * Provide inject an implementation of {@link AbstractStatusChecksProperties} to control the checks.
     */
    @TestExtension
    public static final ChecksProperties PROPERTIES = new ChecksProperties();

    /**
     * Tests when the implementation of {@link AbstractStatusChecksProperties} is not applicable,
     * a status checks should not be published.
     */
    @Test
    public void shouldNotPublishStatusWhenNotApplicable() {
        PROPERTIES.setApplicable(false);

        buildSuccessfully(createFreeStyleProject());

        assertThat(PUBLISHER_FACTORY.getPublishedChecks()).hasSize(0);
    }

    /**
     * Tests when status checks is skipped, a status checks should not be published.
     */
    @Test
    public void shouldNotPublishStatusWhenSkipped() {
        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(true);
        PROPERTIES.setName("Test Status");

        buildSuccessfully(createFreeStyleProject());

        assertThat(PUBLISHER_FACTORY.getPublishedChecks()).hasSize(0);
    }

    /**
     * Tests when an implementation of {@link AbstractStatusChecksProperties} is applicable and not skipped,
     * a status checks using the specified name should be published.
     */
    @Test
    public void shouldPublishStatusWithProperties() {
        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(false);
        PROPERTIES.setName("Test Status");

        buildSuccessfully(createFreeStyleProject());

        assertThat(PUBLISHER_FACTORY.getPublishedChecks()).hasSize(2);

        ChecksDetails details1 = PUBLISHER_FACTORY.getPublishedChecks().get(0);

        assertThat(details1.getName()).isPresent().get().isEqualTo("Test Status");
        assertThat(details1.getStatus()).isEqualTo(ChecksStatus.QUEUED);
        assertThat(details1.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        ChecksDetails details2 = PUBLISHER_FACTORY.getPublishedChecks().get(1);

        assertThat(details2.getName()).isPresent().get().isEqualTo("Test Status");
        assertThat(details2.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(details2.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    /**
     * Test checks output includes pipeline details.
     */
    @Test
    public void shouldPublishStageDetails() {
        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(false);
        PROPERTIES.setName("Test Status");
        WorkflowJob job = createPipeline();

        job.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  stage('Simple Stage') {\n"
                + "  }\n"
                + "  stage('In parallel') {\n"
                + "    parallel 'p1': {\n"
                + "      stage('p1s1') {\n"
                + "        unstable('something went wrong')\n"
                + "      }\n"
                + "      stage('p1s2') {\n"
                + "      }\n"
                + "    }, 'p2': {}\n"
                + "  }\n"
                + "  stage('Fails') {\n"
                + "    error('something went very wrong')\n"
                + "  }\n"
                + "}", true));

        buildWithResult(job, Result.FAILURE);
        PUBLISHER_FACTORY.getPublishedChecks().stream()
                .map(ChecksDetails::getOutput)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(ChecksOutput::getSummary)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(System.out::println);
    }

    static class ChecksProperties extends AbstractStatusChecksProperties {
        private boolean applicable;
        private boolean skipped;
        private String name;

        public void setApplicable(final boolean applicable) {
            this.applicable = applicable;
        }

        public void setSkipped(final boolean skipped) {
            this.skipped = skipped;
        }

        public void setName(final String name) {
            this.name = name;
        }

        @Override
        public boolean isApplicable(final Job<?, ?> job) {
            return applicable;
        }

        @Override
        public String getName(final Job<?, ?> job) {
            return name;
        }

        @Override
        public boolean isSkipped(final Job<?, ?> job) {
            return skipped;
        }
    }
}
