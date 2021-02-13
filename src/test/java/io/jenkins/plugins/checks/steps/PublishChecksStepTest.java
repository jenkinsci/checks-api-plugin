package io.jenkins.plugins.checks.steps;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static io.jenkins.plugins.checks.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublishChecksStepTest {
    private StepContext context;

    @BeforeEach
    void setup() throws IOException, InterruptedException {
        this.context = mock(StepContext.class);
        when(context.get(Run.class)).thenReturn(mock(Run.class));
        when(context.get(TaskListener.class)).thenReturn(TaskListener.NULL);
    }

    @Test
    void shouldPublishCheckWithDefaultValues() throws IOException, InterruptedException {

        StepExecution execution = new PublishChecksStep().start(context);
        assertThat(execution).isInstanceOf(PublishChecksStep.PublishChecksStepExecution.class);
        assertThat(((PublishChecksStep.PublishChecksStepExecution)execution).extractChecksDetails())
                .usingRecursiveComparison()
                .isEqualTo(new ChecksDetails.ChecksDetailsBuilder()
                        .withName(StringUtils.EMPTY)
                        .withStatus(ChecksStatus.COMPLETED)
                        .withConclusion(ChecksConclusion.SUCCESS)
                        .withDetailsURL(StringUtils.EMPTY)
                        .withOutput(new ChecksOutput.ChecksOutputBuilder()
                                .withTitle(StringUtils.EMPTY)
                                .withSummary(StringUtils.EMPTY)
                                .withText(StringUtils.EMPTY)
                                .build())
                        .build());
    }

    @Test
    void shouldPublishCheckWithStatusInProgress() throws IOException, InterruptedException {
        PublishChecksStep step = getModifiedPublishChecksStepObject("an in progress build",
                ChecksStatus.IN_PROGRESS,null);

        StepExecution execution = step.start(context);
        assertThat(execution).isInstanceOf(PublishChecksStep.PublishChecksStepExecution.class);
        assertThat(((PublishChecksStep.PublishChecksStepExecution)execution).extractChecksDetails())
                .usingRecursiveComparison()
                .isEqualTo(new ChecksDetails.ChecksDetailsBuilder()
                        .withName("Jenkins")
                        .withStatus(ChecksStatus.IN_PROGRESS)
                        .withConclusion(ChecksConclusion.NONE)
                        .withDetailsURL("http://ci.jenkins.io")
                        .withOutput(new ChecksOutput.ChecksOutputBuilder()
                                .withTitle("Jenkins Build")
                                .withSummary("a check made by Jenkins")
                                .withText("an in progress build")
                                .build())
                        .build());
    }

    @Test
    void shouldPublishCheckWithStatusQueue() throws IOException, InterruptedException {
        PublishChecksStep step = getModifiedPublishChecksStepObject("a queued build",
                ChecksStatus.QUEUED,null);

        StepExecution execution = step.start(context);
        assertThat(execution).isInstanceOf(PublishChecksStep.PublishChecksStepExecution.class);
        assertThat(((PublishChecksStep.PublishChecksStepExecution)execution).extractChecksDetails())
                .usingRecursiveComparison()
                .isEqualTo(new ChecksDetails.ChecksDetailsBuilder()
                        .withName("Jenkins")
                        .withStatus(ChecksStatus.QUEUED)
                        .withConclusion(ChecksConclusion.NONE)
                        .withDetailsURL("http://ci.jenkins.io")
                        .withOutput(new ChecksOutput.ChecksOutputBuilder()
                                .withTitle("Jenkins Build")
                                .withSummary("a check made by Jenkins")
                                .withText("a queued build")
                                .build())
                        .build());
    }

    @Test
    void shouldPublishCheckWithSetValues() throws IOException, InterruptedException {
        PublishChecksStep step = getModifiedPublishChecksStepObject("a failed build",
                ChecksStatus.IN_PROGRESS, ChecksConclusion.FAILURE);

        StepExecution execution = step.start(context);
        assertThat(execution).isInstanceOf(PublishChecksStep.PublishChecksStepExecution.class);
        assertThat(((PublishChecksStep.PublishChecksStepExecution)execution).extractChecksDetails())
                .usingRecursiveComparison()
                .isEqualTo(new ChecksDetails.ChecksDetailsBuilder()
                        .withName("Jenkins")
                        .withStatus(ChecksStatus.IN_PROGRESS)
                        .withConclusion(ChecksConclusion.FAILURE)
                        .withDetailsURL("http://ci.jenkins.io")
                        .withOutput(new ChecksOutput.ChecksOutputBuilder()
                                .withTitle("Jenkins Build")
                                .withSummary("a check made by Jenkins")
                                .withText("a failed build")
                                .build())
                        .build());
    }

    @Test
    void shouldDefinePublishChecksStepDescriptorCorrectly() {
        PublishChecksStep.PublishChecksStepDescriptor descriptor = new PublishChecksStep.PublishChecksStepDescriptor();
        assertThat(descriptor.getFunctionName()).isEqualTo("publishChecks");
        assertThat(descriptor.getDisplayName()).isEqualTo("Publish customized checks to SCM platforms");
        assertThat(descriptor.getRequiredContext().toArray()).containsExactlyInAnyOrder(Run.class, TaskListener.class);
    }

    private PublishChecksStep getModifiedPublishChecksStepObject(String stepText, ChecksStatus status,
                                                                 ChecksConclusion conclusion) {
        PublishChecksStep step = new PublishChecksStep();
        step.setName("Jenkins");
        step.setSummary("a check made by Jenkins");
        step.setTitle("Jenkins Build");
        step.setText(stepText);
        if (Objects.nonNull(status)) {
            step.setStatus(status);
        }
        if (Objects.nonNull(conclusion)) {
            step.setConclusion(conclusion);
        }
        step.setDetailsURL("http://ci.jenkins.io");

        return step;
    }
}
