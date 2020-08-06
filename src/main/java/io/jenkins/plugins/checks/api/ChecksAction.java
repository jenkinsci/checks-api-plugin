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
 * An action of a check. It can be used to create actions like re-run or automatic formatting.
 */
@Restricted(Beta.class)
@SuppressWarnings("PMD.DataClass")
@SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Empty constructor used by stapler")
public class ChecksAction extends AbstractDescribableImpl<ChecksAction> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String label;
    private String description;
    private String identifier;

    /**
     * Empty constructor used by stapler to support pipeline.
     */
    @DataBoundConstructor
    public ChecksAction() {
        super();
    }

    /**
     * Creates a {@link ChecksAction} using the given parameters.
     *
     * <p>
     *     Note that for a GitHub check run, the {@code label}, {@code description}, and {@code identifier} must not
     *     exceed 20, 40, and 20 characters.
     * </p>
     *
     * @param label
     *         the text to be displayed on a button in web UI
     * @param description
     *         a short explanation of what this action would do
     * @param identifier
     *         a reference for the action on the integrator's system
     */
    @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
    public ChecksAction(@Nullable final String label, @Nullable final String description,
            @Nullable final String identifier) {
        super();

        this.label = label;
        this.description = description;
        this.identifier = identifier;
    }

    @DataBoundSetter
    public void setLabel(final String label) {
        this.label = label;
    }

    @DataBoundSetter
    public void setDescription(final String description) {
        this.description = description;
    }

    @DataBoundSetter
    public void setIdentifier(final String identifier) {
        this.identifier = identifier;
    }

    public Optional<String> getLabel() {
        return Optional.ofNullable(label);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }
}
