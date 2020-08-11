package io.jenkins.plugins.checks.steps;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.*;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.*;

/**
 * Pipeline step to publish customized checks.
 */
@SuppressWarnings("PMD.DataClass")
public class PublishChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String summary;

    private String title;

    private String text = StringUtils.EMPTY;
    private String detailsURL = StringUtils.EMPTY;
    private ChecksStatus status = ChecksStatus.COMPLETED;
    private ChecksConclusion conclusion = ChecksConclusion.SUCCESS;

    /**
     * Constructor used in pipeline with required fields.
     *
     * @param name
     *         the name of the check
     * @param summary
     *         the summary of build
     */
    @DataBoundConstructor
    public PublishChecksStep(final String name, final String summary) {
        super();

        this.name = name;
        this.summary = summary;

        this.title = name;
    }

    @DataBoundSetter
    public void setTitle(final String title) {
        this.title = title;
    }

    @DataBoundSetter
    public void setText(final String text) {
        this.text = text;
    }

    @DataBoundSetter
    public void setDetailsURL(final String detailsURL) {
        this.detailsURL = detailsURL;
    }

    @DataBoundSetter
    public void setStatus(final ChecksStatus status) {
        this.status = status;
    }

    @DataBoundSetter
    public void setConclusion(final ChecksConclusion conclusion) {
        this.conclusion = conclusion;
    }

    @Override
    public StepExecution start(final StepContext stepContext) {
        return new PublishChecksStepExecution(stepContext, this);
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
            ChecksPublisherFactory.fromRun(getContext().get(Run.class), getContext().get(TaskListener.class))
                    .publish(extractChecksDetails());

            return null;
        }

        @VisibleForTesting
        ChecksDetails extractChecksDetails() {
            ChecksDetails.ChecksDetailsBuilder builder = new ChecksDetails.ChecksDetailsBuilder()
                    .withName(step.name)
                    .withStatus(step.status)
                    .withConclusion(step.conclusion)
                    .withOutput(new ChecksOutput.ChecksOutputBuilder()
                            .withTitle(step.title)
                            .withSummary(step.summary)
                            .withText(step.text)
                            .build());

            if (StringUtils.isNotBlank(step.detailsURL)) {
                builder.withDetailsURL(step.detailsURL);
            }

            return builder.build();
        }
    }
}
