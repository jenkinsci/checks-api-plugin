package io.jenkins.plugins.checks.steps;

import java.io.Serializable;
import java.util.Objects;

/**
 * A collection of checks properties that will be injected into {@link WithChecksStep} closure.
 */
public class ChecksInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;

    /**
     * Creates a {@link ChecksInfo} with checks name.
     *
     * @param name
     *         the name of the check
     */
    public ChecksInfo(final String name) {
        Objects.requireNonNull(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
