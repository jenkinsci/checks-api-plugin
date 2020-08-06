package io.jenkins.plugins.checks.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractDescribableImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import static java.util.Objects.*;

/**
 * An output of a check. The output usually contains the most useful information like summary, description,
 * annotations, etc.
 */
@Restricted(Beta.class)
@SuppressWarnings("PMD.DataClass")
@SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Empty constructor used by stapler")
public class ChecksOutput extends AbstractDescribableImpl<ChecksOutput> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String summary;
    private String text;
    private List<ChecksAnnotation> annotations;
    private List<ChecksImage> images;

    /**
     * Empty constructor used by stapler to support pipeline.
     */
    @DataBoundConstructor
    public ChecksOutput() {
        super();
    }

    /**
     * Copy constructor of the {@link ChecksOutput}.
     *
     * @param that
     *         the source to copy from
     */
    public ChecksOutput(final ChecksOutput that) {
        this(that.getTitle().orElse(null), that.getSummary().orElse(null), that.getText().orElse(null),
                that.getChecksAnnotations(), that.getChecksImages());
    }

    private ChecksOutput(final String title, final String summary, final String text,
            final List<ChecksAnnotation> annotations, final List<ChecksImage> images) {
        super();

        this.title = title;
        this.summary = summary;
        this.text = text;
        this.annotations = annotations;
        this.images = images;
    }

    @DataBoundSetter
    public void setTitle(final String title) {
        this.title = title;
    }

    @DataBoundSetter
    public void setSummary(final String summary) {
        this.summary = summary;
    }

    @DataBoundSetter
    public void setText(final String text) {
        this.text = text;
    }

    @DataBoundSetter
    public void setAnnotations(final List<ChecksAnnotation> annotations) {
        this.annotations = annotations;
    }

    @DataBoundSetter
    public void setImages(final List<ChecksImage> images) {
        this.images = images;
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getSummary() {
        return Optional.ofNullable(summary);
    }

    public Optional<String> getText() {
        return Optional.ofNullable(text);
    }

    public List<ChecksAnnotation> getChecksAnnotations() {
        return annotations;
    }

    public List<ChecksImage> getChecksImages() {
        return images;
    }

    /**
     * Builder for {@link ChecksOutput}.
     */
    public static class ChecksOutputBuilder {
        private String title;
        private String summary;
        private String text;
        private List<ChecksAnnotation> annotations;
        private List<ChecksImage> images;

        /**
         * Construct a builder for a {@link ChecksOutput}.
         *
         */
        public ChecksOutputBuilder() {
            this.annotations = new ArrayList<>();
            this.images = new ArrayList<>();
        }

        /**
         * Sets the title of the check run.
         *
         * @param title
         *         the title of the check run
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withTitle(final String title) {
            this.title = requireNonNull(title);
            return this;
        }

        /**
         * Sets the summary of the check run
         *
         * <p>
         *     Note that for the GitHub check runs, the {@code summary} supports Markdown.
         * <p>
         *
         * @param summary
         *         the summary of the check run
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withSummary(final String summary) {
            this.summary = requireNonNull(summary);
            return this;
        }

        /**
         * Adds the details description for a check run. This parameter supports Markdown.
         *
         * <p>
         *     Note that for a GitHub check run, the {@code text} supports Markdown.
         * <p>
         *
         * @param text
         *         the details description in Markdown
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withText(final String text) {
            this.text = requireNonNull(text);
            return this;
        }

        /**
         * Sets the {@link ChecksAnnotation} for a check run.
         *
         * @param annotations
         *         the annotations list
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withAnnotations(final List<ChecksAnnotation> annotations) {
            this.annotations = new ArrayList<>(requireNonNull(annotations));
            return this;
        }

        /**
         * Adds a {@link ChecksAnnotation}.
         *
         * @param annotation
         *         the annotation
         * @return this builder
         */
        public ChecksOutputBuilder addAnnotation(final ChecksAnnotation annotation) {
            annotations.add(new ChecksAnnotation(requireNonNull(annotation)));
            return this;
        }

        /**
         * Sets the {@link ChecksImage} for a check run.
         * @param images
         *         the images list
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withImages(final List<ChecksImage> images) {
            this.images = new ArrayList<>(requireNonNull(images));
            return this;
        }

        /**
         * Adds a {@link ChecksImage}.
         *
         * @param image
         *         the image
         * @return this builder
         */
        public ChecksOutputBuilder addImage(final ChecksImage image) {
            images.add(requireNonNull(image));
            return this;
        }

        /**
         * Actually builds the {@link ChecksOutput} with given parameters.
         *
         * @return the built {@link ChecksOutput}
         */
        public ChecksOutput build() {
            return new ChecksOutput(title, summary, text,
                    Collections.unmodifiableList(annotations),
                    Collections.unmodifiableList(images));
        }
    }
}
