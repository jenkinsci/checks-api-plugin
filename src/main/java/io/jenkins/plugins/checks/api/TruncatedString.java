package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

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
public abstract class TruncatedString {

    @NonNull
    private final String truncationText;
    private final boolean truncateStart;

    protected TruncatedString(@NonNull final String truncationText, final boolean reverse) {
        this.truncationText = Objects.requireNonNull(truncationText);
        this.truncateStart = reverse;
    }

    /**
     * Wrap the provided string as a {@link TruncatedString}.
     *
     * @param string String to wrap as a {@link TruncatedString}
     * @return a {@link TruncatedString} wrapping the provided input
     */
    static TruncatedString fromString(final String string) {
        return new NewlineTruncatedString.Builder().withString(string).build();
    }

    /**
     * Builds the string without truncation.
     *
     * @return A string comprising the joined chunks.
     */
    @Override
    public abstract String toString();

    protected abstract List<String> getChunks();

    /**
     * Builds the string such that it does not exceed maxSize, including the truncation string.
     *
     * @param maxSize the maximum size of the resultant string.
     * @return A string comprising as many of the joined chunks that will fit in the given size, plus the truncation
     * string if truncation was necessary.
     */
    @CheckForNull
    public String build(final int maxSize) {
        List<String> chunks = getChunks();
        if (truncateStart) {
            Collections.reverse(chunks);
        }
        return chunks.stream().collect(new Joiner(maxSize));
    }


    /**
     * Base builder for {@link TruncatedString}.
     *
     * @param <B> the type of {@link TruncatedString} to build
     */
    public abstract static class Builder<B> {
        private String truncationText = "Output truncated.";
        private boolean truncateStart = false;

        protected String getTruncationText() {
            return truncationText;
        }

        protected boolean isTruncateStart() {
            return truncateStart;
        }

        protected abstract B self();

        /**
         * Builds the {@link TruncatedString}.
         *
         * @return the build {@link TruncatedString}.
         */
        public abstract TruncatedString build();

        /**
         * Sets the truncation text.
         *
         * @param truncationText the text to append on overflow
         * @return this builder
         */
        @SuppressWarnings("HiddenField")
        public B withTruncationText(@NonNull final String truncationText) {
            this.truncationText = Objects.requireNonNull(truncationText);
            return self();
        }

        /**
         * Sets truncator to remove excess text from the start, rather than the end.
         *
         * @return this builder
         */
        public B setTruncateStart() {
            this.truncateStart = true;
            return self();
        }

    }

    private class Joiner implements Collector<String, Joiner.Accumulator, String> {

        private final int maxLength;

        Joiner(final int maxLength) {
            if (maxLength < truncationText.length()) {
                throw new IllegalArgumentException("Maximum length is less than truncation text.");
            }
            this.maxLength = maxLength;
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
        public Function<Accumulator, String> finisher() {
            return Accumulator::join;
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
                if (length + chunk.length() > maxLength) {
                    truncated = true;
                    return;
                }
                chunks.add(chunk);
                length += chunk.length();
            }

            String join() {
                if (truncateStart) {
                    Collections.reverse(chunks);
                }
                if (truncated) {
                    if (length + truncationText.length() > maxLength) {
                        chunks.remove(truncateStart ? 0 : chunks.size() - 1);
                    }
                    chunks.add(truncationText);
                }
                return String.join("", chunks);
            }
        }
    }

}
