package io.jenkins.plugins.checks.steps;

import hudson.util.ListBoxModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Utilities for pipeline steps.
 */
public class StepUtils {
    /**
     * Converts an {@link Enum} into a {@link ListBoxModel} with all values of it.
     *
     * @param enums
     *         all candidate values of the enum
     * @return the list box model with all enum values.
     */
    public ListBoxModel asListBoxModel(final Enum<?>... enums) {
        return Arrays.stream(enums)
                .map(Enum::name)
                .map(name -> new ListBoxModel.Option(asDisplayName(name), name))
                .collect(Collectors.toCollection(ListBoxModel::new));
    }

    private String asDisplayName(final String name) {
        return StringUtils.capitalize(name.toLowerCase(Locale.ENGLISH).replace("_", " "));
    }
}
