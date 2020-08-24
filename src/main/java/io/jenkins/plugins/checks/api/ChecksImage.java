package io.jenkins.plugins.checks.api;

import java.util.Optional;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * An image of a check. Users may use a image to show the code coverage, issues trend, etc.
 */
@Restricted(Beta.class)
public class ChecksImage {
    private final String alt;
    private final String imageUrl;
    private final String caption;

    /**
     * Constructs an image with all parameters.
     *
     * @param alt
     *         the alternative text for the image
     * @param imageUrl
     *         the full URL of the image
     * @param caption
     *         a short description of the image
     */
    @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
    public ChecksImage(@CheckForNull final String alt, @CheckForNull final String imageUrl, @CheckForNull final String caption) {
        this.alt = alt;
        this.imageUrl = imageUrl;
        this.caption = caption;
    }

    /**
     * Returns the alternative text for the image.
     *
     * @return the alternative text for the image
     */
    public Optional<String> getAlt() {
        return Optional.ofNullable(alt);
    }

    /**
     * Returns the image URL.
     *
     * @return the image URL
     */
    public Optional<String> getImageUrl() {
        return Optional.ofNullable(imageUrl);
    }

    /**
     * Returns the short description of the image.
     *
     * @return the short description of the image
     */
    public Optional<String> getCaption() {
        return Optional.ofNullable(caption);
    }

    @Override
    public String toString() {
        return "ChecksImage{"
                + "alt='" + alt + '\''
                + ", imageUrl='" + imageUrl + '\''
                + ", caption='" + caption + '\''
                + '}';
    }
}
