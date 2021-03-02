package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.util.Optional;

import static java.util.Objects.*;

/**
 * An annotation for specific lines of code.
 */
@SuppressWarnings("PMD.DataClass")
public class ChecksAnnotation {
    @CheckForNull
    private final String path;
    @CheckForNull
    private final Integer startLine;
    @CheckForNull
    private final Integer endLine;
    @CheckForNull
    private final String message;
    @CheckForNull
    private final Integer startColumn;
    @CheckForNull
    private final Integer endColumn;
    @CheckForNull
    private final String title;
    @CheckForNull
    private final String rawDetails;

    private final ChecksAnnotationLevel annotationLevel;

    @SuppressWarnings("ParameterNumber")
    private ChecksAnnotation(@CheckForNull final String path,
                             @CheckForNull final Integer startLine, @CheckForNull final Integer endLine,
                             final ChecksAnnotationLevel annotationLevel, @CheckForNull final String message,
                             @CheckForNull final Integer startColumn, @CheckForNull final Integer endColumn,
                             @CheckForNull final String title, @CheckForNull final String rawDetails) {
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

    @Override
    public String toString() {
        return "ChecksAnnotation{"
                + "path='" + path + '\''
                + ", startLine=" + startLine
                + ", endLine=" + endLine
                + ", annotationLevel=" + annotationLevel
                + ", message='" + message + '\''
                + ", startColumn=" + startColumn
                + ", endColumn=" + endColumn
                + ", title='" + title + '\''
                + ", rawDetails='" + rawDetails + '\''
                + '}';
    }

    /**
     * Builder for {@link ChecksAnnotation}.
     */
    public static class ChecksAnnotationBuilder {
        @CheckForNull
        private String path;
        @CheckForNull
        private Integer startLine;
        @CheckForNull
        private Integer endLine;
        @CheckForNull
        private String message;
        @CheckForNull
        private Integer startColumn;
        @CheckForNull
        private Integer endColumn;
        @CheckForNull
        private String title;
        @CheckForNull
        private String rawDetails;

        private ChecksAnnotationLevel annotationLevel;

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
