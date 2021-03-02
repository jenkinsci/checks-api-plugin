package io.jenkins.plugins.checks.steps;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.checks.api.ChecksAction;
import io.jenkins.plugins.checks.api.ChecksAnnotation;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
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
    private List<StepChecksAction> actions = Collections.emptyList();
    private List<StepChecksAnnotation> annotations = Collections.emptyList();

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

    @DataBoundSetter
    public void setActions(final List<StepChecksAction> actions) {
        this.actions = actions;
    }

    @DataBoundSetter
    public void setAnnotations(final List<StepChecksAnnotation> annotations) {
        this.annotations = annotations;
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

    public List<StepChecksAction> getActions() {
        return actions;
    }

    public List<StepChecksAnnotation> getAnnotations() {
        return annotations;
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
        private final StepUtils utils = new StepUtils();

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
            return utils.asListBoxModel(ChecksStatus.values());
        }

        /**
         * Fill the dropdown list model with all {@link ChecksConclusion}s.
         *
         * @return a model with all {@link ChecksConclusion}s.
         */
        public ListBoxModel doFillConclusionItems() {
            return utils.asListBoxModel(ChecksConclusion.values());
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
                            .withAnnotations(step.getAnnotations().stream()
                                    .map(StepChecksAnnotation::getAnnotation)
                                    .collect(Collectors.toList()))
                            .build())
                    .withActions(step.getActions().stream()
                            .map(StepChecksAction::getAction)
                            .collect(Collectors.toList()))
                    .build();
        }
    }

    /**
     * A simple wrapper for {@link ChecksAnnotation} to allow users add code annotations by {@link PublishChecksStep}.
     */
    public static class StepChecksAnnotation extends AbstractDescribableImpl<StepChecksAnnotation>
            implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String path;
        private final int startLine;
        private final int endLine;
        private final String message;

        private Integer startColumn;
        private Integer endColumn;
        private String title;
        private String rawDetails;

        private ChecksAnnotation.ChecksAnnotationLevel annotationLevel = ChecksAnnotation.ChecksAnnotationLevel.WARNING;

        /**
         * Creates an annotation with required parameters.
         *
         * @param path
         *         path of the file to annotate
         * @param startLine
         *         start line of the annotation
         * @param endLine
         *         end line of the annotation
         * @param message
         *         annotation message
         */
        @DataBoundConstructor
        @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
                justification = "Null values are a reasonable state implying the user doesn't specify it.")
        public StepChecksAnnotation(final String path, final int startLine, final int endLine, final String message) {
            super();

            this.path = path;
            this.startLine = startLine;
            this.endLine = endLine;
            this.message = message;
        }

        @DataBoundSetter
        public void setStartColumn(final Integer startColumn) {
            this.startColumn = startColumn;
        }

        @DataBoundSetter
        public void setEndColumn(final Integer endColumn) {
            this.endColumn = endColumn;
        }

        @DataBoundSetter
        public void setTitle(final String title) {
            this.title = title;
        }

        @DataBoundSetter
        public void setRawDetails(final String rawDetails) {
            this.rawDetails = rawDetails;
        }

        @DataBoundSetter
        public void setAnnotationLevel(final ChecksAnnotation.ChecksAnnotationLevel annotationLevel) {
            this.annotationLevel = annotationLevel;
        }

        public String getPath() {
            return path;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public String getMessage() {
            return message;
        }

        public Integer getStartColumn() {
            return startColumn;
        }

        public Integer getEndColumn() {
            return endColumn;
        }

        public String getTitle() {
            return title;
        }

        public String getRawDetails() {
            return rawDetails;
        }

        public ChecksAnnotation.ChecksAnnotationLevel getAnnotationLevel() {
            return annotationLevel;
        }

        /**
         * Get {@link ChecksAnnotation} built with user-provided parameters in {@link PublishChecksStep}.
         *
         * @return the annotation built with provided parameters
         */
        public ChecksAnnotation getAnnotation() {
            ChecksAnnotation.ChecksAnnotationBuilder builder = new ChecksAnnotation.ChecksAnnotationBuilder()
                    .withPath(path)
                    .withStartLine(startLine)
                    .withEndLine(endLine)
                    .withMessage(message)
                    .withAnnotationLevel(annotationLevel);

            if (startColumn != null) {
                builder.withStartColumn(startColumn);
            }
            if (endColumn != null) {
                builder.withEndColumn(endColumn);
            }
            if (title != null) {
                builder.withTitle(title);
            }
            if (rawDetails != null) {
                builder.withRawDetails(rawDetails);
            }

            return builder.build();
        }

        /**
         * Descriptor for {@link StepChecksAnnotation}, required for Pipeline Snippet Generator.
         */
        @Extension
        public static class StepChecksAnnotationDescriptor extends Descriptor<StepChecksAnnotation> {
            private final StepUtils utils = new StepUtils();

            /**
             * Fill the dropdown list model with all {@link io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel}
             * values.
             *
             * @return a model with all {@link io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel} values.
             */
            public ListBoxModel doFillAnnotationLevelItems() {
                return utils.asListBoxModel(
                        Arrays.stream(ChecksAnnotation.ChecksAnnotationLevel.values())
                                .filter(v -> v != ChecksAnnotation.ChecksAnnotationLevel.NONE)
                                .toArray(Enum[]::new));
            }
        }
    }

    /**
     * A simple wrapper for {@link ChecksAction} to allow users add checks actions by {@link PublishChecksStep}.
     */
    public static class StepChecksAction extends AbstractDescribableImpl<StepChecksAction> implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String label;
        private final String identifier;
        private String description = StringUtils.EMPTY;

        /**
         * Creates an instance that wraps a newly constructed {@link ChecksAction} with according parameters.
         *
         * @param label
         *         label of the action to display in the checks report on SCMs
         * @param identifier
         *         identifier for the action, useful to identify which action is requested by users
         */
        @DataBoundConstructor
        public StepChecksAction(final String label, final String identifier) {
            super();

            this.label = label;
            this.identifier = identifier;
        }

        @DataBoundSetter
        public void setDescription(final String description) {
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public String getIdentifier() {
            return identifier;
        }

        public ChecksAction getAction() {
            return new ChecksAction(label, description, identifier);
        }

        /**
         * Descriptor for {@link StepChecksAction}, required for Pipeline Snippet Generator.
         */
        @Extension
        public static class StepChecksActionDescriptor extends Descriptor<StepChecksAction> {
        }
    }
}
