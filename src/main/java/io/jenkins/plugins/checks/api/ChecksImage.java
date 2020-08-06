package io.jenkins.plugins.checks.api;

import java.io.Serializable;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractDescribableImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * An image of a check. Users may use a image to show the code coverage, issues trend, etc.
 */
@Restricted(Beta.class)
@SuppressWarnings("PMD.DataClass")
@SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Empty constructor used by stapler")
public class ChecksImage extends AbstractDescribableImpl<ChecksImage> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String alt;
    private String imageUrl;
    private String caption;

    /**
     * Empty constructor used by stapler to support pipeline.
     */
    @DataBoundConstructor
    public ChecksImage() {
        super();
    }

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
    public ChecksImage(@Nullable final String alt, @Nullable final String imageUrl, @Nullable final String caption) {
        super();

        this.alt = alt;
        this.imageUrl = imageUrl;
        this.caption = caption;
    }

    @DataBoundSetter
    public void setAlt(final String alt) {
        this.alt = alt;
    }

    @DataBoundSetter
    public void setImageUrl(final String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @DataBoundSetter
    public void setCaption(final String caption) {
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
}
