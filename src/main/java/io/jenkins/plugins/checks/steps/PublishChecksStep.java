package io.jenkins.plugins.checks.steps;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.*;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Pipeline step to publish customized checks.
 */
@SuppressWarnings("PMD.DataClass")
@SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Empty constructor used by stapler")
public class PublishChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String detailsURL;
    private ChecksStatus status;
    private ChecksConclusion conclusion;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime completedAt = LocalDateTime.now();
    private ChecksOutput output;
    private List<ChecksAction> actions;

    /**
     * Empty constructor used by stapler to support pipeline.
     */
    @DataBoundConstructor
    public PublishChecksStep() {
        super();
    }

    @DataBoundSetter
    public void setName(final String name) {
        this.name = name;
    }

    @DataBoundSetter
    public void setStatus(final ChecksStatus status) {
        this.status = status;
    }

    @DataBoundSetter
    public void setDetailsURL(final String detailsURL) {
        this.detailsURL = detailsURL;
    }

    @DataBoundSetter
    public void setStartedAt(final String startedAt) {
        this.startedAt = LocalDateTime.parse(startedAt);
    }

    @DataBoundSetter
    public void setConclusion(final ChecksConclusion conclusion) {
        this.conclusion = conclusion;
    }

    @DataBoundSetter
    public void setCompletedAt(final String completedAt) {
        this.completedAt = LocalDateTime.parse(completedAt);
    }

    @DataBoundSetter
    public void setOutput(final ChecksOutput output) {
        this.output = output;
    }

    @DataBoundSetter
    public void setActions(final List<ChecksAction> actions) {
        this.actions = actions;
    }

    @Override
    public StepExecution start(final StepContext stepContext) {
        return new PublishChecksStepExecution(stepContext, this);
    }

    /**
     * This step's execution to actually publish checks.
     */
    static class PublishChecksStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;
        private final PublishChecksStep step;

        PublishChecksStepExecution(final StepContext context, final PublishChecksStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            ChecksDetails details = new ChecksDetails.ChecksDetailsBuilder()
                    .withName(step.name)
                    .withDetailsURL(step.detailsURL)
                    .withStatus(step.status)
                    .withConclusion(step.conclusion)
                    .withStartedAt(step.startedAt)
                    .withCompletedAt(step.completedAt)
                    .withOutput(step.output)
                    .withActions(step.actions)
                    .build();

            ChecksPublisher publisher
                    = ChecksPublisherFactory.fromRun(getContext().get(Run.class), getContext().get(TaskListener.class));
            publisher.publish(details);

            return null;
        }
    }

    /**
     * This step's descriptor which defines function name, display name, and context.
     */
    @Extension
    public static class PublishChecksStepDescriptor extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "publishChecks";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, TaskListener.class)));
        }

        @NonNull
        @Override
        public String getDisplayName() { // it's pipeline step, so where does the name shown?
            return "Publish customized checks to SCM platforms";
        }
    }
}
