package io.jenkins.plugins.checks.status;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Test;
import org.junit.internal.Checks;
import org.jvnet.hudson.test.TestExtension;

import java.util.List;
import java.util.regex.Pattern;

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

        assertThat(PUBLISHER_FACTORY.getPublishedChecks()).hasSize(3);

        ChecksDetails details = PUBLISHER_FACTORY.getPublishedChecks().get(0);

        assertThat(details.getName()).isPresent().get().isEqualTo("Test Status");
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.QUEUED);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        details = PUBLISHER_FACTORY.getPublishedChecks().get(1);

        assertThat(details.getName()).isPresent().get().isEqualTo("Test Status");
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        details = PUBLISHER_FACTORY.getPublishedChecks().get(2);

        assertThat(details.getName()).isPresent().get().isEqualTo("Test Status");
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    /**
     * Test checks output includes pipeline details.
     */
    @Test
    public void shouldPublishStageDetails() {
        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(false);
        PROPERTIES.setSuppressLogs(false);
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
                + "    archiveArtifacts artifacts: 'oh dear', fingerprint: true\n"
                + "  }\n"
                + "}", true));

        buildWithResult(job, Result.FAILURE);

        List<ChecksDetails> checksDetails = PUBLISHER_FACTORY.getPublishedChecks();

        assertThat(checksDetails).hasSize(9);

        // Details 0, queued
        ChecksDetails details = checksDetails.get(0);
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.QUEUED);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.NONE);
        assertThat(details.getName()).isPresent().get().isEqualTo("Test Status");
        assertThat(details.getOutput()).isNotPresent();

        // Details 1, first stage
        details = checksDetails.get(1);
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.NONE);
        assertThat(details.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getTitle()).isPresent().get().isEqualTo("In progress");
            assertThat(output.getSummary()).isPresent().get().satisfies(StringUtils::isBlank);
            assertThat(output.getText()).isPresent().get().asString().contains("* Simple Stage *(running)*");
        });

        // Details 2, first stage finished, parallel started
        details = checksDetails.get(2);
        assertThat(details.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getSummary()).isPresent().get().satisfies(StringUtils::isBlank);
            assertThat(output.getText()).isPresent().get().satisfies(text -> {
                assertThat(output.getTitle()).isPresent().get().isEqualTo("In progress");
                assertThat(text).matches(Pattern.compile(".*\\* Simple Stage \\*\\([^)]+\\)\\*.*", Pattern.DOTALL));
                assertThat(text).contains("  * In parallel *(running)*");
            });
        });

        // Details 6, p1s1 has finished and emitted unstable
        details = checksDetails.get(6);
        assertThat(details.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getTitle()).isPresent().get().isEqualTo("In parallel/p1/p1s1: warning in 'unstable' step");
            assertThat(output.getSummary()).isPresent().get().asString().isEqualToIgnoringNewLines(""
                    + "### `In parallel / p1 / p1s1 / Set stage result to unstable`\n"
                    + "Warning in `unstable` step, with arguments `something went wrong`.\n"
                    + "```\n"
                    + "something went wrong\n"
                    + "```\n"
                    + "\n");
            assertThat(output.getText()).isPresent().get().asString().matches(Pattern.compile(".*"
                        + "  \\* Simple Stage \\*\\([^)]+\\)\\*\n"
                        + "  \\* In parallel \\*\\(running\\)\\*\n"
                        + "    \\* p1 \\*\\(running\\)\\*\n"
                        + "      \\* p1s1 \\*\\([^)]+\\)\\*\n"
                        + "        \\*\\*Unstable\\*\\*: \\*something went wrong\\*\n"
                        + "      \\* p1s2 \\*\\(running\\)\\*\n"
                        + "    \\* p2 \\*\\([^)]+\\)\\*\n.*", Pattern.DOTALL));
        });

        // Details 8, final checks
        details = checksDetails.get(8);
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.FAILURE);
        assertThat(details.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getTitle()).isPresent().get().isEqualTo("Fails: error in 'archiveArtifacts' step");
            assertThat(output.getSummary()).isPresent().get().asString().matches(Pattern.compile(".*"
                    + "### `In parallel / p1 / p1s1 / Set stage result to unstable`\\s+"
                    + "Warning in `unstable` step, with arguments `something went wrong`\\.\\s+"
                    + "```\\s+"
                    + "something went wrong\\s+"
                    + "```\\s+"
                    + "### `Fails / Archive the artifacts`\\s+"
                    + "Error in `archiveArtifacts` step\\.\\s+"
                    + "```\\s+"
                    + "No artifacts found that match the file pattern \"oh dear\"\\. Configuration error\\?\\s+"
                    + "```\\s+"
                    + "<details>\\s+"
                    + "<summary>Build log</summary>\\s+"
                    + "```\\s+"
                    + "Archiving artifacts\\s+"
                    + "‘oh dear’ doesn’t match anything\\s+"
                    + "```\\s+"
                    + "</details>\\s+", Pattern.DOTALL));
            assertThat(output.getText()).isPresent().asString().matches(Pattern.compile(".*"
                    + "  \\* Simple Stage \\*\\([^)]+\\)\\*\n"
                    + "  \\* In parallel \\*\\([^)]+\\)\\*\n"
                    + "    \\* p1 \\*\\([^)]+\\)\\*\n"
                    + "      \\* p1s1 \\*\\([^)]+\\)\\*\n"
                    + "        \\*\\*Unstable\\*\\*: \\*something went wrong\\*\n"
                    + "      \\* p1s2 \\*\\([^)]+\\)\\*\n"
                    + "    \\* p2 \\*\\([^)]+\\)\\*\n"
                    + "  \\* Fails \\*\\([^)]+\\)\\*\n"
                    + "    \\*\\*Error\\*\\*: \\*No artifacts found that match the file pattern \"oh dear\". Configuration error\\?\\*\n.*",
                    Pattern.DOTALL));
        });
    }

    /**
     * Test checks output includes pipeline details, but not logs, when requested.
     */
    @Test
    public void shouldPublishStageDetailsWithoutLogsIfRequested() {
        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(false);
        PROPERTIES.setName("Test Status");
        PROPERTIES.setSuppressLogs(true);
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
                + "    archiveArtifacts artifacts: 'oh dear', fingerprint: true\n"
                + "  }\n"
                + "}", true));

        buildWithResult(job, Result.FAILURE);

        List<ChecksDetails> checksDetails = PUBLISHER_FACTORY.getPublishedChecks();

        assertThat(checksDetails).hasSize(9);

        ChecksDetails details = checksDetails.get(8);
        assertThat(details.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(details.getConclusion()).isEqualTo(ChecksConclusion.FAILURE);
        assertThat(details.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getTitle()).isPresent().get().isEqualTo("Fails: error in 'archiveArtifacts' step");
            assertThat(output.getSummary()).isPresent().get().asString().matches(Pattern.compile(".*"
                    + "### `In parallel / p1 / p1s1 / Set stage result to unstable`\\s+"
                    + "Warning in `unstable` step, with arguments `something went wrong`\\.\\s+"
                    + "```\\s+"
                    + "something went wrong\\s+"
                    + "```\\s+"
                    + "### `Fails / Archive the artifacts`\\s+"
                    + "Error in `archiveArtifacts` step\\.\\s+"
                    + "```\\s+"
                    + "No artifacts found that match the file pattern \"oh dear\"\\. Configuration error\\?\\s+"
                    + "```\\s+", Pattern.DOTALL));
            assertThat(output.getText()).isPresent().asString().matches(Pattern.compile(".*"
                            + "  \\* Simple Stage \\*\\([^)]+\\)\\*\n"
                            + "  \\* In parallel \\*\\([^)]+\\)\\*\n"
                            + "    \\* p1 \\*\\([^)]+\\)\\*\n"
                            + "      \\* p1s1 \\*\\([^)]+\\)\\*\n"
                            + "        \\*\\*Unstable\\*\\*: \\*something went wrong\\*\n"
                            + "      \\* p1s2 \\*\\([^)]+\\)\\*\n"
                            + "    \\* p2 \\*\\([^)]+\\)\\*\n"
                            + "  \\* Fails \\*\\([^)]+\\)\\*\n"
                            + "    \\*\\*Error\\*\\*: \\*No artifacts found that match the file pattern \"oh dear\". Configuration error\\?\\*\n.*",
                    Pattern.DOTALL));
        });
    }

    /**
     * Validates the a simple successful pipeline works.
     */
    @Test
    public void shouldPublishSimplePipeline() {
        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(false);
        PROPERTIES.setName("Test Status");
        WorkflowJob job = createPipeline();

        job.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  echo 'Hello, world'"
                + "}", true));

        buildWithResult(job, Result.SUCCESS);

        List<ChecksDetails> checksDetails = PUBLISHER_FACTORY.getPublishedChecks();

        ChecksDetails details = checksDetails.get(1);
        assertThat(details.getOutput()).isPresent().get().satisfies(output -> assertThat(output.getTitle()).isPresent().get().isEqualTo("Success"));
    }

    static class ChecksProperties extends AbstractStatusChecksProperties {
        private boolean applicable;
        private boolean skipped;
        private String name;
        private boolean suppressLogs;

        public void setApplicable(final boolean applicable) {
            this.applicable = applicable;
        }

        public void setSkipped(final boolean skipped) {
            this.skipped = skipped;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public void setSuppressLogs(final boolean suppressLogs) {
            this.suppressLogs = suppressLogs;
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

        @Override
        public boolean isSuppressLogs(final Job<?, ?> job) {
            return suppressLogs;
        }
    }
}
