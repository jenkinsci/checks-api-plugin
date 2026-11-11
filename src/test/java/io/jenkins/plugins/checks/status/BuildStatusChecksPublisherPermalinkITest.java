package io.jenkins.plugins.checks.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.TestExtension;

import hudson.model.Job;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

/** Regresses SCM eligibility which depends on the first completed build's permalinks. */
class BuildStatusChecksPublisherPermalinkITest extends IntegrationTestWithJenkinsPerTest {
    @Test
    void shouldCompleteFirstBuildCheckAfterPermalinkRefresh() throws Exception {
        WorkflowJob job = createPipeline();
        job.setDefinition(new CpsFlowDefinition("echo 'done'", true));
        // GitSCM fallback for a dead multibranch job uses these cached permalink lookups.
        assertThat(job.getLastSuccessfulBuild()).isNull();
        assertThat(job.getLastCompletedBuild()).isNull();

        Factory factory = getJenkins().getInstance().getExtensionList(Factory.class).get(0);
        CountDownLatch published = new CountDownLatch(1);
        AtomicReference<ChecksDetails> completed = new AtomicReference<>();
        factory.setBeforePublish(details -> {
            if (details.getStatus() == ChecksStatus.COMPLETED) {
                completed.set(details);
                published.countDown();
            }
        });

        buildSuccessfully(job);

        assertThat(published.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(completed.get().getName()).contains("Jenkins");
        assertThat(completed.get().getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    @TestExtension
    public static class Factory extends CapturingChecksPublisher.Factory {
        // Reuse the existing in-memory publisher, without a remote SCM service.
    }

    @TestExtension
    public static class CompletedBuildProperties extends AbstractStatusChecksProperties {
        @Override
        public boolean isApplicable(final Job<?, ?> job) {
            return job.getLastSuccessfulBuild() != null || job.getLastCompletedBuild() != null;
        }

        @Override
        public String getName(final Job<?, ?> job) {
            return "Jenkins";
        }

        @Override
        public boolean isSkipped(final Job<?, ?> job) {
            return false;
        }
    }
}
