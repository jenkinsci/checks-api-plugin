package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Implementation of {@link TruncatedString} that truncates strings to the nearest newline.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public class NewlineTruncatedString extends TruncatedString {

    @NonNull
    private final String string;

    private NewlineTruncatedString(@NonNull final String truncationText, final boolean truncateStart, @NonNull final String string) {
        super(truncationText, truncateStart);
        this.string = string;
    }

    @Override
    public String toString() {
        return string;
    }

    @Override
    protected List<String> getChunks() {
        return Arrays.asList(string.split("(?<=\r?\n)"));
    }

    /**
     * Builder for {@link NewlineTruncatedString}.
     */
    public static class Builder extends TruncatedString.Builder<Builder> {

        private String string = "";

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TruncatedString build() {
            return new NewlineTruncatedString(getTruncationText(), isTruncateStart(), string);
        }

        /**
         * Set the string to truncate.
         *
         * @param string the string to truncate
         * @return this builder
         */
        @SuppressWarnings("HiddenField")
        public Builder withString(final String string) {
            this.string = string;
            return this;
        }

    }

}
