package io.jenkins.plugins.checks.status;

import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.checks.util.LoggingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the {@link BuildStatusChecksPublisher} listens to the status of a {@link Run} and publishes status
 * accordingly.
 */
public class BuildStatusChecksPublisherITest extends IntegrationTestWithJenkinsPerTest {
    private static final String STATUS_TEMPLATE = "Published Checks (name: %s, status: %s, conclusion %s)%n";

    /**
     * Provide a {@link io.jenkins.plugins.checks.util.LoggingChecksPublisher} to log details.
     */
    @TestExtension
    public static final LoggingChecksPublisher.Factory PUBLISHER_FACTORY = new LoggingChecksPublisher.Factory();

    /**
     * Provide inject an implementation of {@link AbstractStatusChecksProperties} to control the checks.
     */
    @TestExtension
    public static final ChecksProperties PROPERTIES = new ChecksProperties();

    /**
     * Tests when the implementation of {@link AbstractStatusChecksProperties} is not applicable,
     * a status checks should not be published.
     *
     * @throws IOException if failed getting log from {@link Run}
     */
    @Test
    public void shouldNotPublishStatusWhenNotApplicable() throws IOException {
        PUBLISHER_FACTORY.setFormatter(details -> String.format(STATUS_TEMPLATE,
                details.getName().orElseThrow(() -> new IllegalStateException("Empty check name")),
                details.getStatus(), details.getConclusion()));

        PROPERTIES.setApplicable(false);

        assertThat(JenkinsRule.getLog(buildSuccessfully(createFreeStyleProject())))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "IN_PROGRESS", "NONE"))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "COMPLETED", "SUCCESS"));
    }

    /**
     * Tests when status checks is skipped, a status checks should not be published.
     *
     * @throws IOException if failed getting log from {@link Run}
     */
    @Test
    public void shouldNotPublishStatusWhenSkipped() throws IOException {
        PUBLISHER_FACTORY.setFormatter(details -> String.format(STATUS_TEMPLATE,
                details.getName().orElseThrow(() -> new IllegalStateException("Empty check name")),
                details.getStatus(), details.getConclusion()));

        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(true);
        PROPERTIES.setName("Test Status");

        assertThat(JenkinsRule.getLog(buildSuccessfully(createFreeStyleProject())))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "IN_PROGRESS", "NONE"))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "COMPLETED", "SUCCESS"));
    }

    /**
     * Tests when an implementation of {@link AbstractStatusChecksProperties} is applicable and not skipped,
     * a status checks using the specified name should be published.
     *
     * @throws IOException if failed getting log from {@link Run}
     */
    @Test
    public void shouldPublishStatusWithProperties() throws IOException {
        PUBLISHER_FACTORY.setFormatter(details -> String.format(STATUS_TEMPLATE,
                details.getName().orElseThrow(() -> new IllegalStateException("Empty check name")),
                details.getStatus(), details.getConclusion()));

        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(false);
        PROPERTIES.setName("Test Status");

        Run<?, ?> run = buildSuccessfully(createFreeStyleProject());
        assertThat(JenkinsRule.getLog(run))
                .contains(String.format(STATUS_TEMPLATE, "Test Status", "IN_PROGRESS", "NONE"))
                .contains(String.format(STATUS_TEMPLATE, "Test Status", "COMPLETED", "SUCCESS"));
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
