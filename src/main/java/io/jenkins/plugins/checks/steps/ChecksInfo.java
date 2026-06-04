package io.jenkins.plugins.checks.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.Serializable;
import java.util.Objects;

/**
 * A collection of checks properties that will be injected into {@link WithChecksStep} closure.
 */
public class ChecksInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    @CheckForNull
    private final String detailsURL;

    /**
     * Creates a {@link ChecksInfo} with checks name.
     *
     * @param name
     *         the name of the check
     */
    public ChecksInfo(final String name) {
        Objects.requireNonNull(name);
        this.name = name;
        this.detailsURL = null;
    }

    /**
     * Creates a {@link ChecksInfo} with checks name and optional details URL.
     *
     * @param name
     *         the name of the check
     * @param detailsURL
     *         the custom details URL (optional, can be null)
     */
    public ChecksInfo(final String name, @CheckForNull final String detailsURL) {
        Objects.requireNonNull(name);
        this.name = name;
        this.detailsURL = detailsURL;
    }

    public String getName() {
        return name;
    }

    /**
     * Gets the custom details URL.
     *
     * @return the custom details URL, or null if not set
     */
    @CheckForNull
    public String getDetailsURL() {
        return detailsURL;
    }
}
