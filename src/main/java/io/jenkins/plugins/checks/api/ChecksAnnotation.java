package io.jenkins.plugins.checks.api;

import java.io.Serializable;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractDescribableImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import static java.util.Objects.*;

/**
 * An annotation for specific lines of code.
 */
@Restricted(Beta.class)
@SuppressWarnings("PMD.DataClass")
@SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Empty constructor used by stapler")
public class ChecksAnnotation extends AbstractDescribableImpl<ChecksAnnotation> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String path;
    private Integer startLine;
    private Integer endLine;
    private ChecksAnnotationLevel annotationLevel;
    private String message;
    private Integer startColumn;
    private Integer endColumn;
    private String title;
    private String rawDetails;

    /**
     * Empty constructor used by stapler to support pipeline.
     */
    @DataBoundConstructor
    public ChecksAnnotation() {
        super();
    }

    /**
     * Copy constructor.
     *
     * @param that
     *         the source
     */
    public ChecksAnnotation(final ChecksAnnotation that) {
        this(that.getPath().orElse(null), that.getStartLine().orElse(null), that.getEndLine().orElse(null),
                that.getAnnotationLevel(), that.getMessage().orElse(null), that.getStartColumn().orElse(null),
                that.getEndColumn().orElse(null), that.getTitle().orElse(null),
                that.getRawDetails().orElse(null));
    }

    @SuppressWarnings("ParameterNumber")
    private ChecksAnnotation(final String path,
            final Integer startLine, final Integer endLine,
            final ChecksAnnotationLevel annotationLevel,
            final String message,
            final Integer startColumn, final Integer endColumn,
            final String title,
            final String rawDetails) {
        super();

        this.path = path;
        this.startLine = startLine;
        this.endLine = endLine;
        this.annotationLevel = annotationLevel;
        this.message = message;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
        this.title = title;
        this.rawDetails = rawDetails;
    }

    @DataBoundSetter
    public void setPath(final String path) {
        this.path = path;
    }

    @DataBoundSetter
    public void setStartLine(final int startLine) {
        this.startLine = startLine;
    }

    @DataBoundSetter
    public void setEndLine(final int endLine) {
        this.endLine = endLine;
    }

    @DataBoundSetter
    public void setAnnotationLevel(final String annotationLevel) {
        this.annotationLevel = ChecksAnnotationLevel.valueOf(annotationLevel);
    }

    @DataBoundSetter
    public void setMessage(final String message) {
        this.message = message;
    }

    @DataBoundSetter
    public void setStartColumn(final int startColumn) {
        this.startColumn = startColumn;
    }

    @DataBoundSetter
    public void setEndColumn(final int endColumn) {
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

    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    public Optional<Integer> getStartLine() {
        return Optional.ofNullable(startLine);
    }

    public Optional<Integer> getEndLine() {
        return Optional.ofNullable(endLine);
    }

    public ChecksAnnotationLevel getAnnotationLevel() {
        return annotationLevel;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public Optional<Integer> getStartColumn() {
        return Optional.ofNullable(startColumn);
    }

    public Optional<Integer> getEndColumn() {
        return Optional.ofNullable(endColumn);
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getRawDetails() {
        return Optional.ofNullable(rawDetails);
    }

    /**
     * The level represents the severity of the annotation.
     */
    public enum ChecksAnnotationLevel {
        NONE,
        NOTICE,
        WARNING,
        FAILURE
    }

    /**
     * Builder for {@link ChecksAnnotation}.
     */
    public static class ChecksAnnotationBuilder {
        private String path;
        private Integer startLine;
        private Integer endLine;
        private ChecksAnnotationLevel annotationLevel;
        private String message;
        private Integer startColumn;
        private Integer endColumn;
        private String title;
        private String rawDetails;

        /**
         * Constructs a builder for {@link ChecksAnnotation}.
         */
        public ChecksAnnotationBuilder() {
            this.annotationLevel = ChecksAnnotationLevel.NONE;
        }

        /**
         * Sets the path of the file to annotate.
         *
         * @param path
         *         the relative path of the file to annotation,
         *         e.g. src/main/java/io/jenkins/plugins/checks/api/ChecksAnnotation.java
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withPath(final String path) {
            this.path = requireNonNull(path);
            return this;
        }

        /**
         * Sets the line of the single line annotation.
         *
         * @param line
         *         the line of code to annotate
         * @return this builder
         */
        public ChecksAnnotationBuilder withLine(final int line) {
            withStartLine(line);
            withEndLine(line);
            return this;
        }

        /**
         * Sets the start line of annotation.
         *
         * @param startLine
         *         the start line of code to annotate
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withStartLine(final Integer startLine) {
            this.startLine = requireNonNull(startLine);
            return this;
        }

        /**
         * Sets the end line of annotation.
         *
         * @param endLine
         *         the end line of code to annotate
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withEndLine(final Integer endLine) {
            this.endLine = requireNonNull(endLine);
            return this;
        }

        /**
         * Sets the annotation level, one of {@code NOTICE}, {@code WARNING}, or {@code FAILURE}.
         * The default is {@code WARNING}.
         *
         * @param level
         *         the annotation level
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withAnnotationLevel(final ChecksAnnotationLevel level) {
            this.annotationLevel = requireNonNull(level);
            return this;
        }

        /**
         * Sets a short description of the feedback for the annotation.
         *
         * @param message
         *         a short description
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withMessage(final String message) {
            this.message = requireNonNull(message);
            return this;
        }

        /**
         * Adds start column of the annotation.
         *
         * @param startColumn
         *         the start column of the annotation
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withStartColumn(final Integer startColumn) {
            this.startColumn = requireNonNull(startColumn);
            return this;
        }

        /**
         * Adds end column of the annotation.
         *
         * @param endColumn
         *         the end column of the annotation
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withEndColumn(final Integer endColumn) {
            this.endColumn = requireNonNull(endColumn);
            return this;
        }

        /**
         * Adds the title that represents the annotation.
         *
         * <p>
         *     Note that for a GitHub check run annotation, the {@code title} must not exceed 255 characters.
         * </p>
         *
         * @param title
         *         the title of the annotation
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withTitle(final String title) {
            this.title = requireNonNull(title);
            return this;
        }

        /**
         * Adds the details about this annotation.
         *
         * <p>
         *     Note that for a GitHub check run annotation, the {@code rawDetails} must not exceed 64 KB.
         * </p>
         *
         * @param rawDetails
         *         the details about this annotation
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksAnnotationBuilder withRawDetails(final String rawDetails) {
            this.rawDetails = requireNonNull(rawDetails);
            return this;
        }

        /**
         * Actually builds the {@link ChecksAnnotation}.
         *
         * @return the built {@link ChecksAnnotation}
         */
        public ChecksAnnotation build() {
            return new ChecksAnnotation(path, startLine, endLine, annotationLevel, message, startColumn, endColumn,
                    title, rawDetails);
        }
    }
}
