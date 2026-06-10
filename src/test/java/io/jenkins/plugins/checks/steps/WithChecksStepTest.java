package io.jenkins.plugins.checks.steps;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WithChecksStepTest {
    @Test
    void shouldStartWithCorrectExecution() throws IOException, InterruptedException {
        StepContext context = mock(StepContext.class);

        when(context.get(Run.class)).thenReturn(mock(Run.class));
        when(context.get(TaskListener.class)).thenReturn(TaskListener.NULL);

        ChecksInfo checksInfo = ((WithChecksStep.WithChecksStepExecution) new WithChecksStep("test").start(context))
                .extractChecksInfo();

        assertThat(checksInfo.getName()).isEqualTo("test");
        assertThat(checksInfo.getDetailsURL()).isNull();
    }

    @Test
    void shouldStartWithCorrectExecutionWithDetailsURL() throws IOException, InterruptedException {
        StepContext context = mock(StepContext.class);
        String customUrl = "https://example.com/custom/details";

        when(context.get(Run.class)).thenReturn(mock(Run.class));
        when(context.get(TaskListener.class)).thenReturn(TaskListener.NULL);

        WithChecksStep step = new WithChecksStep("test");
        step.setDetailsURL(customUrl);

        ChecksInfo checksInfo = ((WithChecksStep.WithChecksStepExecution) step.start(context))
                .extractChecksInfo();

        assertThat(checksInfo.getName()).isEqualTo("test");
        assertThat(checksInfo.getDetailsURL()).isEqualTo(customUrl);
    }

    @Test
    void shouldDefinePublishChecksStepDescriptorCorrectly() {
        WithChecksStep.WithChecksStepDescriptor descriptor = new WithChecksStep.WithChecksStepDescriptor();
        assertThat(descriptor.getFunctionName()).isEqualTo("withChecks");
        assertThat(descriptor.getRequiredContext().toArray()).containsExactlyInAnyOrder(Run.class, TaskListener.class);
    }
}
