package io.jenkins.plugins.checks.steps;

import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher.Factory;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
class WithChecksStepITest extends IntegrationTestWithJenkinsPerTest {
    /**
     * Tests that the step can inject the {@link ChecksInfo} into the closure.
     */
    @Test
    public void shouldInjectChecksInfoIntoClosure() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { assertChecksInfoInjection('test injection') }"));

        buildSuccessfully(job);
    }

    private CapturingChecksPublisher.Factory getFactory() {
        return getJenkins().getInstance().getExtensionList(ChecksPublisherFactory.class)
                .stream()
                .filter(f -> f instanceof Factory)
                .map(f -> (Factory) f)
                .findAny()
                .orElseThrow(() -> new AssertionError("No CapturingChecksPublisher registered as @TestExtension?"));
    }

    /**
     * Clear captured checks on the {@link CapturingChecksPublisher.Factory} after each test.
     */
    @AfterEach
    public void clearPublisher() {
        getFactory().getPublishedChecks().clear();
    }

    /**
     * Test that the publishChecks step picks up names from the withChecks context.
     */
    @Test
    public void publishChecksShouldTakeNameFromWithChecks() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { publishChecks() }"));

        buildSuccessfully(job);

        assertThat(getFactory().getPublishedChecks().size()).isEqualTo(2);
        ChecksDetails autoChecks = getFactory().getPublishedChecks().get(0);
        ChecksDetails manualChecks = getFactory().getPublishedChecks().get(1);

        assertThat(autoChecks.getName()).isPresent().get().isEqualTo("test injection");
        assertThat(autoChecks.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(autoChecks.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        assertThat(manualChecks.getName()).isPresent().get().isEqualTo("test injection");
        assertThat(manualChecks.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(manualChecks.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    @Test
    public void publishChecksShouldIncludeEnclosingBlocksWhenEnabled() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks(name: 'tests', includeStage: true) {}"));

        buildSuccessfully(job);

        assertThat(getFactory().getPublishedChecks()).hasSize(1);
        ChecksDetails autoChecks = getFactory().getPublishedChecks().get(0);

        assertThat(autoChecks.getName()).contains("tests / Integration Test");
        assertThat(autoChecks.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(autoChecks.getConclusion()).isEqualTo(ChecksConclusion.NONE);
    }

    /**
     * Tests that withChecks step ignores names from the withChecks context if one has been explicitly set.
     */
    @Test
    public void publishChecksShouldTakeNameFromWithChecksUnlessOverridden() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { publishChecks name: 'test override' }"));

        buildSuccessfully(job);

        assertThat(getFactory().getPublishedChecks().size()).isEqualTo(2);
        ChecksDetails autoChecks = getFactory().getPublishedChecks().get(0);
        ChecksDetails manualChecks = getFactory().getPublishedChecks().get(1);

        assertThat(autoChecks.getName()).isPresent().get().isEqualTo("test injection");
        assertThat(autoChecks.getStatus()).isEqualTo(ChecksStatus.IN_PROGRESS);
        assertThat(autoChecks.getConclusion()).isEqualTo(ChecksConclusion.NONE);

        assertThat(manualChecks.getName()).isPresent().get().isEqualTo("test override");
        assertThat(manualChecks.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(manualChecks.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
    }

    /**
     * Test that withChecks correctly reports failures.
     */
    @Test
    public void withChecksShouldDetectFailure() {
        WorkflowJob job = createPipeline();
        job.setDefinition(asStage("withChecks('test injection') { error 'oh no!' }"));

        buildWithResult(job, Result.FAILURE);

        assertThat(getFactory().getPublishedChecks().size()).isEqualTo(2);
        ChecksDetails failure = getFactory().getPublishedChecks().get(1);

        assertThat(failure.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(failure.getConclusion()).isEqualTo(ChecksConclusion.FAILURE);
        assertThat(failure.getOutput()).isPresent();

        assertThat(failure.getOutput().get().getText()).isPresent();
        assertThat(failure.getOutput().get().getText().get()).contains("oh no!");
    }

    /**
     * Test that withChecks correctly reports aborts.
     */
    @Test
    public void withChecksShouldDetectAbort() throws Exception {
        WorkflowJob job = createPipeline();
        // Simulate a job cancellation.
        job.setDefinition(new CpsFlowDefinition("withChecks('test injection') { throw new org.jenkinsci.plugins.workflow.steps.FlowInterruptedException("
                + "hudson.model.Result.ABORTED, new org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.RemovedNodeCause()) }", false));

        buildWithResult(job, Result.ABORTED);

        assertThat(getFactory().getPublishedChecks().size()).isEqualTo(2);
        ChecksDetails abort = getFactory().getPublishedChecks().get(1);

        assertThat(abort.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(abort.getConclusion()).isEqualTo(ChecksConclusion.CANCELED);
        assertThat(abort.getOutput()).isPresent();

        assertThat(abort.getOutput().get().getText()).isPresent();
        assertThat(abort.getOutput().get().getText().get()).isEqualTo(new ExecutorStepExecution.RemovedNodeCause().getShortDescription());
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

    /**
     * Provide a {@link CapturingChecksPublisher} to check published checks on each test.
     */
    @TestExtension
    public static class CapturingChecksPublisherTestExtension extends CapturingChecksPublisher.Factory {
        // activate test extension
    }
}
