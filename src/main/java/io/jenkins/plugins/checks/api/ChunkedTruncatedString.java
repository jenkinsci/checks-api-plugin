package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link TruncatedString} that truncates strings to the nearest supplied chunk. This allows for
 * ensuring that, for example, markdown syntax is not truncated midway through a ``` block, or a table, that would
 * result in poor formatting or confusing output.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public class ChunkedTruncatedString extends TruncatedString {

    @NonNull
    private final List<String> chunks;

    private ChunkedTruncatedString(@NonNull final String truncationText, final boolean truncateStart, @NonNull final List<String> chunks) {
        super(truncationText, truncateStart);
        this.chunks = Collections.unmodifiableList(chunks);
    }

    @Override
    public String toString() {
        return String.join("", chunks);
    }

    @Override
    protected List<String> getChunks() {
        return new ArrayList<>(chunks);
    }

    /**
     * Builder for {@link ChunkedTruncatedString}.
     */
    public static class Builder extends TruncatedString.Builder<Builder> {
        private final List<String> chunks = new ArrayList<>();

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TruncatedString build() {
            return new ChunkedTruncatedString(getTruncationText(), isTruncateStart(), chunks);
        }

        /**
         * Adds a chunk of text to the buidler.
         *
         * @param chunk the chunk of text to append to this builder
         * @return this buidler
         */
        public Builder addText(@NonNull final String chunk) {
            this.chunks.add(Objects.requireNonNull(chunk));
            return this;
        }
    }
}
