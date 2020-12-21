package io.jenkins.plugins.checks.steps;

import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.api.test.CapturingChecksPublisher;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.*;
import org.junit.After;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the "withChecks" step.
 */
public class WithChecksStepITest extends IntegrationTestWithJenkinsPerTest {

    /**
     * Tests that the step can inject the {@link ChecksInfo} into the closure.
     */
    @Test
    public void shouldInjectChecksInfoIntoClosure() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { assertChecksInfoInjection('test injection') }"));

        buildSuccessfully(job);
    }

    /**
     * Provide a {@link CapturingChecksPublisher} to check published checks on each test.
     */
    @TestExtension
    public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();

    /**
     * Clear captured checks on the {@link WithChecksStepITest#PUBLISHER_FACTORY} after each test.
     */
    @After
    public void clearPublisher() {
        PUBLISHER_FACTORY.getPublishedChecks().clear();
    }

    /**
     * Test that the publishChecks step picks up names from the withChecks context.
     */
    @Test
    public void publishChecksShouldTakeNameFromWithChecks() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { publishChecks() }"));

        buildSuccessfully(job);

        assertThat(PUBLISHER_FACTORY.getPublishedChecks().size()).isEqualTo(2);
        ChecksDetails autoChecks = PUBLISHER_FACTORY.getPublishedChecks().get(0);
        ChecksDetails manualChecks = PUBLISHER_FACTORY.getPublishedChecks().get(1);

        assertThat(autoChecks.getName()).isPresent().get().isEqualTo("test injection");
        assertThat(autoChecks.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(autoChecks.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        assertThat(manualChecks.getName()).isPresent().get().isEqualTo("test injection");
        assertThat(manualChecks.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(manualChecks.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    /**
     * Tests that withChecks step ignores names from the withChecks context if one has been explicitly set.
     */
    @Test
    public void publishChecksShouldTakeNameFromWithChecksUnlessOverridden() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { publishChecks name: 'test override' }"));

        buildSuccessfully(job);

        assertThat(PUBLISHER_FACTORY.getPublishedChecks().size()).isEqualTo(2);
        ChecksDetails autoChecks = PUBLISHER_FACTORY.getPublishedChecks().get(0);
        ChecksDetails manualChecks = PUBLISHER_FACTORY.getPublishedChecks().get(1);

        assertThat(autoChecks.getName()).isPresent().get().isEqualTo("test injection");
        assertThat(autoChecks.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(autoChecks.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        assertThat(manualChecks.getName()).isPresent().get().isEqualTo("test override");
        assertThat(manualChecks.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(manualChecks.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    /**
     * Assert that the injected {@link ChecksInfo} is as expected.
     */
    @TestExtension
    public static class AssertChecksInfoInjectionStep extends Step implements Serializable {
        private static final long serialVersionUID = 1L;

        private final ChecksInfo expected;

        /**
         * Required by {@link TestExtension} annotation.
         */
        public AssertChecksInfoInjectionStep() {
            this("");
        }

        /**
         * Creates the step with expected name injected.
         *
         * @param expectedName
         *         expected name that will be injected
         */
        @DataBoundConstructor
        public AssertChecksInfoInjectionStep(final String expectedName) {
            super();

            expected = new ChecksInfo(expectedName);
        }

        @Override
        public StepExecution start(final StepContext stepContext) {
            return new AssertChecksInfoInjectionStepExecution(stepContext, this);
        }

        /**
         * Descriptor for {@link AssertChecksInfoInjectionStep} that defines function name and required context.
         */
        @TestExtension
        public static class AssertChecksInfoStepInjectionDescriptor extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "assertChecksInfoInjection";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, ChecksInfo.class)));
            }
        }

        static class AssertChecksInfoInjectionStepExecution extends SynchronousNonBlockingStepExecution<Void> {
            private static final long serialVersionUID = 1L;

            private final AssertChecksInfoInjectionStep step;

            AssertChecksInfoInjectionStepExecution(final StepContext context, final AssertChecksInfoInjectionStep step) {
                super(context);

                this.step = step;
            }

            @Override
            protected Void run() throws Exception {
                assertThat(getContext().get(ChecksInfo.class))
                        .usingRecursiveComparison()
                        .isEqualTo(step.expected);
                return null;
            }
        }
    }
}
