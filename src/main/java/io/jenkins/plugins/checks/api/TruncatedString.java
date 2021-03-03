package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Utility wrapper that silently truncates output with a message at a certain size.
 * <p>
 * The GitHub Checks API has a size limit on text fields. Because it also accepts markdown, it is not trivial to
 * truncate to the required length as this could lead to unterminated syntax. The use of this class allows for adding
 * chunks of complete markdown until an overflow is detected, at which point a message will be added and all future
 * additions will be silently discarded.
 */
public class TruncatedString {

    @NonNull
    private final List<String> chunks;
    @NonNull
    private final String truncationText;
    private final boolean truncateStart;
    private final boolean chunkOnNewlines;


    private TruncatedString(@NonNull final List<String> chunks, @NonNull final String truncationText, final boolean truncateStart, final boolean chunkOnNewlines) {
        this.chunks = Collections.unmodifiableList(Objects.requireNonNull(chunks));
        this.truncationText = Objects.requireNonNull(truncationText);
        this.truncateStart = truncateStart;
        this.chunkOnNewlines = chunkOnNewlines;
    }

    /**
     * Wrap the provided string as a {@link TruncatedString}.
     *
     * @param string String to wrap as a {@link TruncatedString}
     * @return a {@link TruncatedString} wrapping the provided input
     */
    static TruncatedString fromString(final String string) {
        return new Builder().setChunkOnNewlines().addText(string).build();
    }

    /**
     * Builds the string without truncation.
     *
     * @return A string comprising the joined chunks.
     */
    @Override
    public String toString() {
        return String.join("", chunks);
    }

    private List<String> getChunks() {
        if (chunkOnNewlines) {
            return Arrays.asList(String.join("", chunks).split("(?<=\r?\n)"));
        }
        return new ArrayList<>(chunks);
    }

    /**
     * Builds the string such that it does not exceed maxSize in bytes, including the truncation string.
     *
     * @param maxSize the maximum size of the resultant string.
     * @return A string comprising as many of the joined chunks that will fit in the given size, plus the truncation
     * string if truncation was necessary.
     * @deprecated use the explicit {@link #buildByBytes(int)} or {@link #buildByChars(int)} method according to your requirements.
     */
    @Deprecated
    public String build(final int maxSize) {
        return build(maxSize, false);
    }

    /**
     * Builds the string such that it does not exceed maxSize in bytes, including the truncation string.
     *
     * @param maxSize the maximum size of the resultant string.
     * @return A string comprising as many of the joined chunks that will fit in the given size, plus the truncation
     * string if truncation was necessary.
     */
    public String buildByBytes(final int maxSize) {
        return build(maxSize, false);
    }

    /**
     * Builds the string such that it does not exceed maxSize in chars, including the truncation string.
     *
     * @param maxSize the maximum size of the resultant string.
     * @return A string comprising as many of the joined chunks that will fit in the given size, plus the truncation
     * string if truncation was necessary.
     */
    public String buildByChars(final int maxSize) {
        return build(maxSize, true);
    }

    private String build(final int maxSize, final boolean chunkOnChars) {
        List<String> parts = getChunks();
        if (truncateStart) {
            Collections.reverse(parts);
        }
        List<String> truncatedParts = parts.stream().collect(new Joiner(truncationText, maxSize, chunkOnChars));
        if (truncateStart) {
            Collections.reverse(truncatedParts);
        }
        return String.join("", truncatedParts);
    }


    /**
     * Builder for {@link TruncatedString}.
     */
    public static class Builder {
        private String truncationText = "Output truncated.";
        private boolean truncateStart = false;
        private boolean chunkOnNewlines = false;
        private final List<String> chunks = new ArrayList<>();

        /**
         * Builds the {@link TruncatedString}.
         *
         * @return the build {@link TruncatedString}.
         */
        public TruncatedString build() {
            return new TruncatedString(chunks, truncationText, truncateStart, chunkOnNewlines);
        }

        /**
         * Adds a chunk of text to the builder.
         *
         * @param text the chunk of text to append to this builder
         * @return this builder
         */
        public Builder addText(@NonNull final String text) {
            this.chunks.add(Objects.requireNonNull(text));
            return this;
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
         * Sets truncator to remove excess text from the start, rather than the end.
         *
         * @return this builder
         */
        public Builder setTruncateStart() {
            this.truncateStart = true;
            return this;
        }

        /**
         * Sets truncator to chunk on newlines rather than the chunks.
         *
         * @return this builder
         */
        public Builder setChunkOnNewlines() {
            this.chunkOnNewlines = true;
            return this;
        }

    }

    private static class Joiner implements Collector<String, Joiner.Accumulator, List<String>> {

        private final int maxLength;
        private final String truncationText;
        private final boolean chunkOnChars;

        Joiner(final String truncationText, final int maxLength, final boolean chunkOnChars) {
            this.truncationText = truncationText;
            this.maxLength = maxLength;
            this.chunkOnChars = chunkOnChars;
            if (maxLength < getLength(truncationText)) {
                throw new IllegalArgumentException("Maximum length is less than truncation text.");
            }
        }

        private int getLength(String text) {
            return chunkOnChars ? text.length() : text.getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public Supplier<Joiner.Accumulator> supplier() {
            return Accumulator::new;
        }

        @Override
        public BiConsumer<Joiner.Accumulator, String> accumulator() {
            return Accumulator::add;
        }

        @Override
        public BinaryOperator<Accumulator> combiner() {
            return Accumulator::combine;
        }

        @Override
        public Function<Accumulator, List<String>> finisher() {
            return Accumulator::truncate;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }

        private class Accumulator {
            private final List<String> chunks = new ArrayList<>();
            private int length = 0;
            private boolean truncated = false;

            Accumulator combine(final Accumulator other) {
                other.chunks.forEach(this::add);
                return this;
            }

            void add(final String chunk) {
                if (truncated) {
                    return;
                }
                if (length + getLength(chunk) > maxLength) {
                    truncated = true;
                    return;
                }
                chunks.add(chunk);
                length += getLength(chunk);
            }

            List<String> truncate() {
                if (truncated) {
                    if (length + getLength(truncationText) > maxLength) {
                        chunks.remove(chunks.size() - 1);
                    }
                    chunks.add(truncationText);
                }
                return chunks;
            }
        }
    }

}
