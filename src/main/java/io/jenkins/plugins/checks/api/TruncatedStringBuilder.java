package io.jenkins.plugins.checks.api;

/**
 * {@link StringBuilder} wrapper that silently truncates output with a message at a certain size.
 *
 * The GitHub Checks API has a size limit on text fields. Because it also accepts markdown, it is not trivial to
 * truncate to the required length as this could lead to unterminated syntax. The use of this class allows for adding
 * chunks of complete markdown until an overflow is detected, at which point a message will be added and all future
 * additions will be silently discarded.
 */
@SuppressWarnings("PMD.AvoidStringBufferField")
public class TruncatedStringBuilder {

    private final StringBuilder builder = new StringBuilder();
    private final int maxSize;
    private final String truncatedMessage;
    private boolean full = false;

    /**
     * Create a {@link TruncatedStringBuilder} with the provided limit and truncation message.
     *
     * @param maxSize the size which the wrapped {@link StringBuilder} should not exceed.
     * @param truncatedMessage the message to be appended should maxSize be exceeded.
     */
    public TruncatedStringBuilder(final int maxSize, final String truncatedMessage) {
        this.maxSize = maxSize;
        this.truncatedMessage = truncatedMessage;
    }

    /**
     * Append the provided {@link CharSequence} to the wrapped {@link StringBuilder} if
     *
     * 1. We have not already exceeded maxSize (even if the new value would fit)
     * 2. The existing size, plus the size of the new value, plus the size of the truncation message is less than maxSize.
     *
     * @param s the value to append.
     * @return this.
     */
    public TruncatedStringBuilder append(final CharSequence s) {
        if (full) {
            return this;
        }
        if (builder.length() + s.length() + truncatedMessage.length() > maxSize) {
            builder.append(truncatedMessage);
            full = true;
            return this;
        }
        builder.append(s);
        return this;
    }

    public boolean isFull() {
        return full;
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
