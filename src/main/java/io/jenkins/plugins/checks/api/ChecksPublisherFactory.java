package io.jenkins.plugins.checks.api;

import java.util.List;
import java.util.Optional;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.model.Job;
import hudson.model.TaskListener;
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
     * Creates a {@link ChecksPublisher} according to the {@link hudson.scm.SCM} used by the {@link Job}.
     *
     * @param job
     *         a Jenkins job
     * @param listener
     *         a listener to the builds
     * @return the created {@link ChecksPublisher}
     */
    protected abstract Optional<ChecksPublisher> createPublisher(Job<?, ?> job, TaskListener listener);

    /**
     * Returns a suitable publisher for the run.
     *
     * @param job
     *         a Jenkins job
     * @param listener
     *         a listener for the builds
     * @return a publisher suitable for the job
     */
    public static ChecksPublisher fromJob(final Job<?, ?> job, final TaskListener listener) {
        return fromJob(job, listener, new JenkinsFacade());
    }

    @VisibleForTesting
    static ChecksPublisher fromJob(final Job<?, ?> job, final TaskListener listener,
                                   final JenkinsFacade jenkinsFacade) {
        return findAllPublisherFactories(jenkinsFacade).stream()
                .map(factory -> factory.createPublisher(job, listener))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(new NullChecksPublisher());
    }

    private static List<ChecksPublisherFactory> findAllPublisherFactories(final JenkinsFacade jenkinsFacade) {
        return jenkinsFacade.getExtensionsFor(ChecksPublisherFactory.class);
    }
}
