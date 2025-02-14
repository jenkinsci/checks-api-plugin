package io.jenkins.plugins.checks.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static java.util.Objects.*;

/**
 * An output of a check. The output usually contains the most useful information like summary, description,
 * annotations, etc.
 */
public class ChecksOutput {
    @CheckForNull
    private final String title;
    @CheckForNull
    private final TruncatedString summary;
    @CheckForNull
    private final TruncatedString text;

    private final List<ChecksAnnotation> annotations;
    private final List<ChecksImage> images;

    private ChecksOutput(@CheckForNull final String title, @CheckForNull final TruncatedString summary,
                         @CheckForNull final TruncatedString text, final List<ChecksAnnotation> annotations,
                         final List<ChecksImage> images) {
        this.title = title;
        this.summary = summary;
        this.text = text;
        this.annotations = annotations;
        this.images = images;
    }

    /**
     * Copy constructor of the {@link ChecksOutput}.
     *
     * @param that
     *         the source to copy from
     */
    public ChecksOutput(final ChecksOutput that) {
        this(that.getTitle().orElse(null),
                that.getSummary().map(TruncatedString::fromString).orElse(null),
                that.getText().map(TruncatedString::fromString).orElse(null),
                that.getChecksAnnotations(), that.getChecksImages());
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getSummary() {
        return Optional.ofNullable(summary).map(TruncatedString::toString);
    }

    /**
     * Get the output summary, truncated by {@link TruncatedString} to maxSize.
     *
     * @param maxSize maximum size to truncate summary to.
     * @return Summary, truncated to maxSize with truncation message if appropriate.
     */
    public Optional<String> getSummary(final int maxSize) {
        if (summary == null) {
            return Optional.empty();
        }
        return truncateSummary(summary, maxSize);
    }

    public Optional<String> getText() {
        return Optional.ofNullable(text).map(TruncatedString::toString);
    }

    /**
     * Get the output text, truncated by {@link TruncatedString} to maxSize.
     *
     * @param maxSize maximum size to truncate text to.
     * @return Text, truncated to maxSize with truncation message if appropriate.
     */
    public Optional<String> getText(final int maxSize) {
        return Optional.ofNullable(text).map(s -> s.build(maxSize));
    }

    public List<ChecksAnnotation> getChecksAnnotations() {
        return annotations;
    }

    public List<ChecksImage> getChecksImages() {
        return images;
    }

    @Override
    public String toString() {
        return "ChecksOutput{"
                + "title='" + title + '\''
                + ", summary='" + summary + '\''
                + ", text='" + text + '\''
                + ", annotations=" + annotations
                + ", images=" + images
                + '}';
    }

    /**
     * Truncates the summary to the given maxSize. Tries to truncate from start of build log section if possible.
     *
     * @param summaryToTruncate the summary to truncate
     * @param maxSize the maximum size to truncate to
     * @return the truncated summary
     */
    private Optional<String> truncateSummary(final TruncatedString summaryToTruncate, final int maxSize) {
        String content = summaryToTruncate.toString();
        if (!content.contains("<summary>")) {
            return Optional.of(summaryToTruncate.build(maxSize));
        }

        // Find the build log section
        int detailsStart = content.indexOf("<details>");
        int detailsEnd = content.indexOf("</details>") + "</details>".length();
        
        if (detailsStart == -1 || detailsEnd == -1) {
            return Optional.of(summaryToTruncate.build(maxSize));
        }

        // Split into pre-details, details block, and post-details
        String preDetails = content.substring(0, detailsStart);
        String details = content.substring(detailsStart, detailsEnd);
        String postDetails = content.substring(detailsEnd);

        // Find the actual log content within the details
        int logStart = details.indexOf("```\n") + 4;
        int logEnd = details.lastIndexOf("\n```");
        
        if (logStart == -1 || logEnd == -1) {
            return Optional.of(summaryToTruncate.build(maxSize));
        }

        String beforeLog = details.substring(0, logStart);
        String log = details.substring(logStart, logEnd);
        String afterLog = details.substring(logEnd);

        // Calculate available space for log
        int nonLogLength = preDetails.length() + beforeLog.length() + afterLog.length() + postDetails.length();
        int availableForLog = maxSize - nonLogLength;

        if (availableForLog <= 0) {
            // If no space for log, truncate the whole content
            return Optional.of(summaryToTruncate.build(maxSize));
        }

        // Truncate the log using TruncatedString
        log = new TruncatedString.Builder()
                .setChunkOnNewlines()
                .setTruncateStart()
                .withTruncationText("Build log truncated.\n")
                .addText(log)
                .build()
                .buildByChars(availableForLog);

        // Reconstruct the content
        String truncatedContent = preDetails + beforeLog + log + afterLog + postDetails;
        return Optional.of(truncatedContent);
    }

    /**
     * Builder for {@link ChecksOutput}.
     */
    @SuppressWarnings("ParameterHidesMemberVariable")
    public static class ChecksOutputBuilder {
        @CheckForNull
        private String title;
        @CheckForNull
        private TruncatedString summary;
        @CheckForNull
        private TruncatedString text;

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
         * </p>
         *
         * @param summary
         *         the summary of the check run
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withSummary(final String summary) {
            return withSummary(TruncatedString.fromString(summary));
        }

        /**
         * Sets the summary of the check run, using a {@link TruncatedString}.
         *
         * <p>
         *     Note that for the GitHub check runs, the {@code summary} supports Markdown.
         * </p>
         *
         * @param summary
         *         the summary of the check run as a {@link TruncatedString}
         * @return this builder
         */
        @SuppressWarnings("HiddenField")
        public ChecksOutputBuilder withSummary(final TruncatedString summary) {
            this.summary = requireNonNull(summary);
            return this;
        }

        /**
         * Adds the details description for a check run. This parameter supports Markdown.
         *
         * <p>
         *     Note that for a GitHub check run, the {@code text} supports Markdown.
         * </p>
         *
         * @param text
         *         the details description in Markdown
         * @return this builder
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksOutputBuilder withText(final String text) {
            return withText(TruncatedString.fromString(text));
        }

        /**
         * Adds the details description for a check run, using a {@link TruncatedString}. This parameter supports Markdown.
         *
         * <p>
         *     Note that for a GitHub check run, the {@code text} supports Markdown.
         * </p>
         *
         * @param text
         *         the details description in Markdown as a {@link TruncatedString}
         * @return this builder
         */
        @SuppressWarnings("HiddenField")
        public ChecksOutputBuilder withText(final TruncatedString text) {
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
