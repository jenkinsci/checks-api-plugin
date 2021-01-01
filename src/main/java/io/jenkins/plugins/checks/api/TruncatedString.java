package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility wrapper that silently truncates output with a message at a certain size.
 *
 * The GitHub Checks API has a size limit on text fields. Because it also accepts markdown, it is not trivial to
 * truncate to the required length as this could lead to unterminated syntax. The use of this class allows for adding
 * chunks of complete markdown until an overflow is detected, at which point a message will be added and all future
 * additions will be silently discarded.
 */
public class TruncatedString {

    @NonNull
    private final List<CharSequence> chunks;
    @NonNull
    private final String truncationText;

    /**
     * Create a {@link TruncatedString} with the provided chunks and truncation message.
     *
     * @param truncationText the message to be appended should maxSize be exceeded, e.g.
     *                       "Some output is not shown here, see more on [Jenkins](url)."
     * @param chunks a list of {@link CharSequence}s that are to be concatenated.
     */
    private TruncatedString(@NonNull final String truncationText, @NonNull final List<CharSequence> chunks) {
        this.truncationText = Objects.requireNonNull(truncationText);
        this.chunks = Objects.requireNonNull(chunks);
    }

    /**
     * Wrap the provided string as a {@link TruncatedString}.
     *
     * @param string String to wrap as a {@link TruncatedString}
     * @return a {@link TruncatedString} wrapping the provided input
     */
    static TruncatedString fromString(final String string) {
        return new TruncatedString.Builder().addText(string).build();
    }

    @Override
    public String toString() {
        return String.join("", chunks);
    }

    /**
     * Builds the string without truncation.
     *
     * @return A string comprising the joined chunks.
     */
    @CheckForNull
    public String build() {
        return chunks.isEmpty() ? null : String.join("", chunks);
    }

    /**
     * Builds the string such that it does not exceed maxSize, including the truncation string.
     *
     * @param maxSize the maximum size of the resultant string.
     * @return A string comprising as many of the joined chunks that will fit in the given size, plus the truncation
     * string if truncation was necessary.
     */
    @CheckForNull
    public String build(final int maxSize) {
        if (chunks.isEmpty()) {
            return null;
        }
        String quickJoin = String.join("", chunks);
        if (quickJoin.length() <= maxSize) {
            return quickJoin;
        }
        StringBuilder builder = new StringBuilder();
        for (CharSequence chunk: chunks) {
            if (builder.length() + chunk.length() + truncationText.length() < maxSize) {
                builder.append(chunk);
            }
            else {
                builder.append(truncationText);
                break;
            }
        }
        return builder.toString();
    }

    /**
     * Builder for {@link TruncatedString}.
     */
    public static class Builder {
        private String truncationText = "Output truncated.";
        private final List<CharSequence> chunks = new ArrayList<>();

        /**
         * Builds the {@link TruncatedString}.
         *
         * @return the build {@link TruncatedString}.
         */
        public TruncatedString build() {
            return new TruncatedString(truncationText, chunks);
        }

        /**
         * Sets the truncation text.
         *
         * @param truncationText the text to append on overflow
         * @return this builder
         */
        @SuppressWarnings("HiddenField")
        public Builder withTruncationText(@NonNull final String truncationText) {
            this.truncationText = Objects.requireNonNull(truncationText);
            return this;
        }

        /**
         * Adds a chunk of text to the buidler.
         *
         * @param chunk the chunk of text to append to this builder
         * @return this buidler
         */
        public Builder addText(@NonNull final CharSequence chunk) {
            this.chunks.add(Objects.requireNonNull(chunk));
            return this;
        }

    }

}
