package io.jenkins.plugins.checks.api;

import java.util.List;
import java.util.Optional;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.model.Job;
import hudson.model.Run;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import hudson.ExtensionPoint;

import io.jenkins.plugins.checks.api.ChecksPublisher.NullChecksPublisher;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * A publisher API for consumers to publish checks.
 */
@Restricted(Beta.class)
public abstract class ChecksPublisherFactory implements ExtensionPoint {
    /**
     * Creates a {@link ChecksPublisher} according to the {@link hudson.scm.SCM} used by the {@link Run}.
     *
     * <p>
     * If you don't want to create publisher for the run, return {@code Optional.empty()}.
     * </p>
     *
     * @param run
     *         a Jenkins run
     * @return the created {@link ChecksPublisher}
     */
    protected abstract Optional<ChecksPublisher> createPublisher(Run<?, ?> run);

    /**
     * Creates a {@link ChecksPublisher} according to the {@link hudson.scm.SCM} used by the {@link Job}.
     *
     * <p>
     * By default，it will return {@code Optional.empty()} thus lead to a {@link NullChecksPublisher}.
     * </p>
     *
     * <p>
     * This method will be useful if you want create publisher for {@link hudson.model.Queue.Item} since you can cast
     * the belonged {@link hudson.model.Queue.Task} to {@link Job}.
     * </p>
     *
     * @param job
     *         a Jenkins job
     * @return the created {@link ChecksPublisher}
     */
    protected Optional<ChecksPublisher> createPublisher(Job<?, ?> job) {
        return Optional.empty();
    }

    /**
     * Returns a suitable publisher for the run.
     *
     * @param run
     *         a Jenkins run
     * @return a publisher suitable for the job
     */
    public static ChecksPublisher fromRun(final Run<?, ?> run) {
        return fromRun(run, new JenkinsFacade());
    }

    /**
     * Returns a suitable publisher for the job.
     *
     * @param job
     *         a Jenkins job
     * @return a publisher suitable for the job
     */
    public static ChecksPublisher fromJob(final Job<?, ?> job) {
        return fromJob(job, new JenkinsFacade());
    }

    @VisibleForTesting
    static ChecksPublisher fromRun(final Run<?, ?> run, final JenkinsFacade jenkinsFacade) {
        return findAllPublisherFactories(jenkinsFacade).stream()
                .map(factory -> factory.createPublisher(run))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(new NullChecksPublisher());
    }

    @VisibleForTesting
    static ChecksPublisher fromJob(final Job<?, ?> job, final JenkinsFacade jenkinsFacade) {
        return findAllPublisherFactories(jenkinsFacade).stream()
                .map(factory -> factory.createPublisher(job))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(new NullChecksPublisher());
    }

    private static List<ChecksPublisherFactory> findAllPublisherFactories(final JenkinsFacade jenkinsFacade) {
        return jenkinsFacade.getExtensionsFor(ChecksPublisherFactory.class);
    }
}
