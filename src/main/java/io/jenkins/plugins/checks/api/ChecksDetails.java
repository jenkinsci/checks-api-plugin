package io.jenkins.plugins.checks.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static java.util.Objects.*;

/**
 * Details of a check. This class is a top class which contains all parameters needed for a check.
 */
@SuppressWarnings("PMD.DataClass")
public class ChecksDetails {
    @CheckForNull
    private final String name;
    @CheckForNull
    private final String detailsURL;
    @CheckForNull
    private final LocalDateTime startedAt;
    @CheckForNull
    private final LocalDateTime completedAt;
    @CheckForNull
    private final ChecksOutput output;

    private final ChecksStatus status;
    private final ChecksConclusion conclusion;
    private final List<ChecksAction> actions;

    @SuppressWarnings("ParameterNumber")
    private ChecksDetails(@CheckForNull final String name, final ChecksStatus status,
                          @CheckForNull final String detailsURL, @CheckForNull final LocalDateTime startedAt,
                          final ChecksConclusion conclusion, @CheckForNull final LocalDateTime completedAt,
                          @CheckForNull final ChecksOutput output, final List<ChecksAction> actions) {
        this.name = name;
        this.status = status;
        this.detailsURL = detailsURL;
        this.startedAt = startedAt;
        this.conclusion = conclusion;
        this.completedAt = completedAt;
        this.output = output;
        this.actions = actions;
    }

    /**
     * Returns the name of a check.
     *
     * @return the unique name of a check
     */
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the status of a check.
     *
     * @return {@link ChecksStatus}, one of {@code QUEUED}, {@code IN_PROGRESS}, {@code COMPLETED}.
     */
    public ChecksStatus getStatus() {
        return status;
    }

    /**
     * Returns the url of a site with full details of a check.
     *
     * @return the url of a site
     */
    public Optional<String> getDetailsURL() {
        return Optional.ofNullable(detailsURL);
    }

    /**
     * Returns the time that the check started.
     *
     * @return the start time of a check
     */
    public Optional<LocalDateTime> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    /**
     * Returns the conclusion of a check.
     *
     * @return the conclusion of a check
     */
    public ChecksConclusion getConclusion() {
        return conclusion;
    }

    /**
     * Returns the time that the check completed.
     *
     * @return the complete time of a check
     */
    public Optional<LocalDateTime> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    /**
     * Returns the {@link ChecksOutput} of a check.
     *
     * @return An {@link ChecksOutput} of a check
     */
    public Optional<ChecksOutput> getOutput() {
        return Optional.ofNullable(output);
    }

    /**
     * Returns the {@link ChecksAction}s of a check.
     *
     * @return An immutable list of {@link ChecksAction}s of a check
     */
    public List<ChecksAction> getActions() {
        return actions;
    }

    @Override
    public String toString() {
        return "ChecksDetails{"
                + "name='" + name + '\''
                + ", detailsURL='" + detailsURL + '\''
                + ", status=" + status
                + ", conclusion=" + conclusion
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", output=" + output
                + ", actions=" + actions
                + '}';
    }

    /**
     * Builder for {@link ChecksDetails}.
     */
    @SuppressWarnings("ParameterHidesMemberVariable")
    public static class ChecksDetailsBuilder {
        @CheckForNull
        private String name;
        @CheckForNull
        private String detailsURL;
        @CheckForNull
        private LocalDateTime startedAt;
        @CheckForNull
        private LocalDateTime completedAt;
        @CheckForNull
        private ChecksOutput output;

        private ChecksStatus status;
        private ChecksConclusion conclusion;
        private List<ChecksAction> actions;

        /**
         * Construct a builder for {@link ChecksDetails}.
         */
        public ChecksDetailsBuilder() {
            this.status = ChecksStatus.NONE;
            this.conclusion = ChecksConclusion.NONE;
            this.actions = new ArrayList<>();
        }

        /**
         * Set the name of the check.
         *
         * <p>
         *     Note that for GitHub check runs, the name shown on GitHub UI will be the same as this attribute and
         *     GitHub uses this attribute to identify a check run, so make sure this name is unique, e.g. "Coverage".
         * </p>
         *
         * @param name
         *         the check's name
         * @return this builder
         * @throws NullPointerException if the {@code name} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withName(final String name) {
            this.name = requireNonNull(name);
            return this;
        }

        /**
         * Set the status of the check.
         *
         * <p>
         *     Note that for a GitHub check run, if the status is not set, the default "queued" will be used.
         * </p>
         *
         * @param status
         *         the check's status
         * @return this builder
         * @throws NullPointerException if the {@code status} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withStatus(final ChecksStatus status) {
            this.status = requireNonNull(status);
            return this;
        }

        /**
         * Set the url of a site with full details of a check.
         *
         * <p>
         *     Note that for a GitHub check run, the url must use http or https scheme.
         * </p>
         *
         * <p>
         *     If the details url is not set, the Jenkins build url will be used,
         *     e.g. https://ci.jenkins.io/job/Core/job/jenkins/job/master/2000/.
         * </p>
         *
         * @param detailsURL
         *         the url using http or https scheme
         * @return this builder
         * @throws NullPointerException if the {@code detailsURL} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withDetailsURL(final String detailsURL) {
            this.detailsURL = requireNonNull(detailsURL);
            return this;
        }

        /**
         * Set the time when a check starts.
         *
         * @param startedAt
         *         the time when a check starts
         * @return this builder
         * @throws NullPointerException if the {@code startAt} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withStartedAt(final LocalDateTime startedAt) {
            this.startedAt = requireNonNull(startedAt);
            return this;
        }

        /**
         * Set the conclusion of a check.
         *
         * <p>
         *     Note that for a GitHub check run, the conclusion should only be set when the {@code status}
         *     was {@link ChecksStatus#COMPLETED}.
         * </p>
         *
         * @param conclusion
         *         the conclusion
         * @return this builder
         * @throws NullPointerException if the {@code conclusion} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withConclusion(final ChecksConclusion conclusion) {
            this.conclusion = requireNonNull(conclusion);
            return this;
        }

        /**
         * Set the time when a check completes.
         *
         * @param completedAt
         *         the time when a check completes
         * @return this builder
         * @throws NullPointerException if the {@code completedAt} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withCompletedAt(final LocalDateTime completedAt) {
            this.completedAt = requireNonNull(completedAt);
            return this;
        }

        /**
         * Set the output of a check.
         *
         * @param output
         *         an output of a check
         * @return this builder
         * @throws NullPointerException if the {@code outputs} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withOutput(final ChecksOutput output) {
            this.output = new ChecksOutput(requireNonNull(output));
            return this;
        }

        /**
         * Set the actions of a check.
         *
         * @param actions
         *         a list of actions
         * @return this builder
         * @throws NullPointerException if the {@code actions} is null
         */
        @SuppressWarnings("HiddenField") // builder pattern
        public ChecksDetailsBuilder withActions(final List<ChecksAction> actions) {
            this.actions = new ArrayList<>(requireNonNull(actions));
            return this;
        }

        /**
         * Adds a {@link ChecksAction}.
         *
         * @param action
         *         the action
         * @return this builder
         */
        public ChecksDetailsBuilder addAction(final ChecksAction action) {
            actions.add(requireNonNull(action));
            return this;
        }

        /**
         * Actually build the {@code ChecksDetail}.
         *
         * @return the built {@code ChecksDetail}
         */
        public ChecksDetails build() {
            return new ChecksDetails(name, status, detailsURL, startedAt, conclusion, completedAt, output,
                    Collections.unmodifiableList(actions));
        }
    }
}
