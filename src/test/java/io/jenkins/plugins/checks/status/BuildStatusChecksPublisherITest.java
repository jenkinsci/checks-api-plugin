package io.jenkins.plugins.checks.status;

import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.checks.util.LoggingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildStatusChecksPublisherITest extends IntegrationTestWithJenkinsPerTest {
    private static final String STATUS_TEMPLATE = "Published Checks (name: %s, status: %s, conclusion %s)%n";

    /**
     * Provide a {@link io.jenkins.plugins.checks.util.LoggingChecksPublisher} to log details.
     */
    @TestExtension
    public static final LoggingChecksPublisher.Factory PUBLISHER_FACTORY = new LoggingChecksPublisher.Factory();

    @TestExtension
    public static final ChecksProperties PROPERTIES = new ChecksProperties();

    @Test
    public void shouldNotPublishStatusWhenNotApplicable() throws IOException {
        PUBLISHER_FACTORY.setFormatter(details -> String.format(STATUS_TEMPLATE,
                details.getName().orElse(StringUtils.EMPTY), details.getStatus(), details.getConclusion()));

        PROPERTIES.setApplicable(false);

        assertThat(JenkinsRule.getLog(buildSuccessfully(createFreeStyleProject())))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "IN_PROGRESS", "NONE"))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "COMPLETED", "SUCCESS"));
    }

    @Test
    public void shouldNotPublishStatusWhenSkipped() throws IOException {
        PUBLISHER_FACTORY.setFormatter(details -> String.format(STATUS_TEMPLATE,
                details.getName().orElse(StringUtils.EMPTY), details.getStatus(), details.getConclusion()));

        PROPERTIES.setApplicable(true);
        PROPERTIES.setSkipped(true);
        PROPERTIES.setName("Test Status");

        assertThat(JenkinsRule.getLog(buildSuccessfully(createFreeStyleProject())))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "IN_PROGRESS", "NONE"))
                .doesNotContain(String.format(STATUS_TEMPLATE, "Test Status", "COMPLETED", "SUCCESS"));

    }

    @Test
    public void shouldPublishStatusWithProperties() throws IOException {
        PUBLISHER_FACTORY.setFormatter(details -> String.format(STATUS_TEMPLATE,
                details.getName().orElse(StringUtils.EMPTY), details.getStatus(), details.getConclusion()));

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
