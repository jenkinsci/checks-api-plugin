package io.jenkins.plugins.checks.api;

import java.util.List;
import java.util.Optional;

import hudson.model.Queue;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import hudson.ExtensionPoint;
import hudson.model.Run;

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
     * @param run
     *         a Jenkins run
     * @return the created {@link ChecksPublisher}
     */
    protected abstract Optional<ChecksPublisher> createPublisher(Run<?, ?> run);

    /**
     * Creates a {@link ChecksPublisher} according to the {@link hudson.scm.SCM} used by the {@link Run}.
     *
     * @param item
     *         an item in the queue
     * @return the created {@link ChecksPublisher}
     */
    protected abstract Optional<ChecksPublisher> createPublisher(Queue.Item item);

    /**
     * Returns a suitable publisher for the run.
     *
     * @param run
     *         a Jenkins build
     * @return a publisher suitable for the run
     */
    public static ChecksPublisher fromRun(final Run<?, ?> run) {
        return findAllPublisherFactories().stream()
                .map(factory -> factory.createPublisher(run))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(new NullChecksPublisher());
    }

    /**
     * Returns a suitable publisher for the waiting item.
     *
     * @param item
     *         an item in the queue
     * @return a publisher suitable for the run
     */
    public static ChecksPublisher fromItem(final Queue.Item item) {
        return findAllPublisherFactories().stream()
                .map(factory -> factory.createPublisher(item))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(new NullChecksPublisher());
    }

    private static List<ChecksPublisherFactory> findAllPublisherFactories() {
        return new JenkinsFacade().getExtensionsFor(ChecksPublisherFactory.class);
    }
}
