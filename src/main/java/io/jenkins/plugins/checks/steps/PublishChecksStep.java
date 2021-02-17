package io.jenkins.plugins.checks.steps;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.checks.api.*;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline step to publish customized checks.
 */
@SuppressWarnings("PMD.DataClass")
public class PublishChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name = StringUtils.EMPTY;
    private String summary = StringUtils.EMPTY;
    private String title = StringUtils.EMPTY;
    private String text = StringUtils.EMPTY;
    private String detailsURL = StringUtils.EMPTY;
    private ChecksStatus status = ChecksStatus.COMPLETED;
    private ChecksConclusion conclusion = ChecksConclusion.SUCCESS;

    /**
     * Constructor used for pipeline by Stapler.
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
    public void setSummary(final String summary) {
        this.summary = summary;
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

    /**
     * Change the status of the check.
     * When the {@code status} is {@link ChecksStatus#QUEUED} or {@link ChecksStatus#IN_PROGRESS},
     * the conclusion will be reset to {@link ChecksConclusion#NONE}
     *
     * @param status
     *         the status to be set
     */
    @DataBoundSetter
    public void setStatus(final ChecksStatus status) {
        this.status = status;
        if (status == ChecksStatus.QUEUED || status == ChecksStatus.IN_PROGRESS) {
            this.conclusion = ChecksConclusion.NONE;
        }
    }

    @DataBoundSetter
    public void setConclusion(final ChecksConclusion conclusion) {
        this.conclusion = conclusion;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getTitle() {
        return StringUtils.defaultIfEmpty(title, name);
    }

    public String getText() {
        return text;
    }

    public String getDetailsURL() {
        return detailsURL;
    }

    public ChecksStatus getStatus() {
        return status;
    }

    public ChecksConclusion getConclusion() {
        return conclusion;
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
        public String getDisplayName() {
            return "Publish customized checks to SCM platforms";
        }

        /**
         * Fill the dropdown list model with all {@link ChecksStatus}es.
         *
         * @return a model with all {@link ChecksStatus}es.
         */
        public ListBoxModel doFillStatusItems() {
            return asListBoxModel(ChecksStatus.values());
        }

        /**
         * Fill the dropdown list model with all {@link ChecksConclusion}s.
         *
         * @return a model with all {@link ChecksConclusion}s.
         */
        public ListBoxModel doFillConclusionItems() {
            return asListBoxModel(ChecksConclusion.values());
        }

        private ListBoxModel asListBoxModel(final Enum<?>... enums) {
            return Arrays.stream(enums)
                    .map(Enum::name)
                    .map(name -> new ListBoxModel.Option(asDisplayName(name), name))
                    .collect(Collectors.toCollection(ListBoxModel::new));
        }

        private String asDisplayName(final String name) {
            return StringUtils.capitalize(name.toLowerCase(Locale.ENGLISH).replace("_", " "));
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
        protected Void run() throws IOException, InterruptedException {
            ChecksPublisherFactory.fromRun(getContext().get(Run.class), getContext().get(TaskListener.class))
                    .publish(extractChecksDetails());

            return null;
        }

        @VisibleForTesting
        ChecksDetails extractChecksDetails() throws IOException, InterruptedException {
            // If a checks name has been provided as part of the step, use that.
            // If not, check to see if there is an active ChecksInfo context (e.g. from withChecks).
            String checksName = StringUtils.defaultIfEmpty(step.getName(),
                    Optional.ofNullable(getContext().get(ChecksInfo.class))
                            .map(ChecksInfo::getName)
                            .orElse(StringUtils.EMPTY)
            );
            return new ChecksDetails.ChecksDetailsBuilder()
                    .withName(checksName)
                    .withStatus(step.getStatus())
                    .withConclusion(step.getConclusion())
                    .withDetailsURL(step.getDetailsURL())
                    .withOutput(new ChecksOutput.ChecksOutputBuilder()
                            .withTitle(step.getTitle())
                            .withSummary(step.getSummary())
                            .withText(step.getText())
                            .build())
                    .build();
        }
    }
}
