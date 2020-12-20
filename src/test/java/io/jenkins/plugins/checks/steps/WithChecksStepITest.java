package io.jenkins.plugins.checks.steps;

import hudson.model.Run;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.*;
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
