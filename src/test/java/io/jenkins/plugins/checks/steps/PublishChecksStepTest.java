package io.jenkins.plugins.checks.steps;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksAction;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static io.jenkins.plugins.checks.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublishChecksStepTest {
    @Test
    void shouldPublishCheckWithDefaultValues() throws IOException, InterruptedException {
        StepExecution execution = new PublishChecksStep().start(createStepContext());
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
                        .withActions(Collections.emptyList())
                        .build());
    }

    @Test
    void shouldPublishCheckWithStatusInProgress() throws IOException, InterruptedException {
        PublishChecksStep step = createPublishChecksStep("an in progress build", ChecksStatus.IN_PROGRESS,
                ChecksConclusion.NONE);

        StepExecution execution = step.start(createStepContext());
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
    void shouldPublishCheckWithStatusQueued() throws IOException, InterruptedException {
        PublishChecksStep step = createPublishChecksStep("a queued build", ChecksStatus.QUEUED,
                ChecksConclusion.NONE);

        StepExecution execution = step.start(createStepContext());
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
        PublishChecksStep step = createPublishChecksStep("a failed build", ChecksStatus.IN_PROGRESS,
                ChecksConclusion.FAILURE);

        List<PublishChecksStep.StepChecksAction> actions = Arrays.asList(
                new PublishChecksStep.StepChecksAction("label-1", "identifier-1"),
                new PublishChecksStep.StepChecksAction("label-2", "identifier-2"));
        actions.get(1).setDescription("description-2");

        step.setActions(actions);
        assertThat(step.getActions().stream().map(PublishChecksStep.StepChecksAction::getLabel))
                .containsExactlyInAnyOrder("label-1", "label-2");
        assertThat(step.getActions().stream().map(PublishChecksStep.StepChecksAction::getDescription))
                .containsExactlyInAnyOrder(StringUtils.EMPTY, "description-2");
        assertThat(step.getActions().stream().map(PublishChecksStep.StepChecksAction::getIdentifier))
                .containsExactlyInAnyOrder("identifier-1", "identifier-2");

        StepExecution execution = step.start(createStepContext());
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
                        .withActions(Arrays.asList(
                                new ChecksAction("label-1", "", "identifier-1"),
                                new ChecksAction("label-2", "description-2", "identifier-2")))
                        .build());
    }

    @Test
    void shouldDefinePublishChecksStepDescriptorCorrectly() {
        PublishChecksStep.PublishChecksStepDescriptor descriptor = new PublishChecksStep.PublishChecksStepDescriptor();
        assertThat(descriptor.getFunctionName()).isEqualTo("publishChecks");
        assertThat(descriptor.getDisplayName()).isEqualTo("Publish customized checks to SCM platforms");
        assertThat(descriptor.getRequiredContext().toArray()).containsExactlyInAnyOrder(Run.class, TaskListener.class);
    }

    private StepContext createStepContext() throws IOException, InterruptedException {
        StepContext context = mock(StepContext.class);
        when(context.get(Run.class)).thenReturn(mock(Run.class));
        when(context.get(TaskListener.class)).thenReturn(TaskListener.NULL);
        return context;
    }

    private PublishChecksStep createPublishChecksStep(final String stepText, final ChecksStatus status,
                                                      final ChecksConclusion conclusion) {
        PublishChecksStep step = new PublishChecksStep();
        step.setName("Jenkins");
        step.setSummary("a check made by Jenkins");
        step.setTitle("Jenkins Build");
        step.setDetailsURL("http://ci.jenkins.io");
        step.setText(stepText);
        step.setStatus(status);
        step.setConclusion(conclusion);

        return step;
    }
}
