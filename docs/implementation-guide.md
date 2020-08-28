# Implementation Guide

This document will show you how to implement the checks API for a specific SCM platform.

## Overview

There are two basic steps to implement this API:
1. Tell the API when to provide your publisher to consumers -- implement `ChecksPublisherFactory`
2. Use the parameters from consumers to publish checks -- implement `ChecksPublisher`

*Tip: while you are implementing this API, you can take the [GitHub Checks Plugin](https://github.com/jenkinsci/github-checks-plugin) as an example.*

## Implement `ChecksPublisherFactory`

All `ChecksPublisher`s are created by `ChecksPublisherFactory`.

When consumers are requesting a publisher, they will invoke the `fromRun` or `fromJob` method in `ChecksPublisherFactory`.
These two static factory methods will enumerate all implementations and try to create a suitable publisher for the `Run` or `Job`.
If no suitable publisher found, a `NullCheckksPublisher` which does nothing will be returned.

Your task is to override the `createPublisher` method and return an instance of your `ChecksPublisher` when the `Run` or `Job` fit.

For example, when implementing the GitHub checks publisher, the GitHub App credentials is a prerequesite.
Thus, you may want to check if the users have configured the GitHub App credentials for the project:
```
@Extension
public class GitHubChecksPublisherFactory extends ChecksPublisherFactory {
    @Override
    protected Optional<ChecksPublisher> createPublisher(final Run<?, ?> run, final TaskListener listener) {
        if (hasGitHubAppCredentials(run)) {
            return Optiona.of(new GitHubChecksPublisher(run));
        }

        return Optional.empty();
    }

    @Override
    protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
        if (hasGitHubAppCredentials(job)) {
            return Optiona.of(new GitHubChecksPublisher(job));
        }

        return Optional.empty();
    }

    // ...
}
```

## Implement `ChecksPublisher`

After getting the `ChecksPublisher`, the consumers will then invoke the `publish` method and pass their checks parameters in.

By overriding the `publish` method, you should publish checks to the target SCM platform based on the checks parameters or some other context parameters (like repository, branch, commit) from the `Run` or `Job`.

```
public class GitHubChecksPublisher extends ChecksPublisher {
    private static final Logger LOGGER = Logger.getLogger(GitHubChecksPublisher.class.getName());

    private final Job<?, ?> job;

    /**
     * Creates a publisher from run.
     *
     * @param run
     *         a Jenkins run
     */
    public GitHubChecksPublisher(final Run<?, ?> run) {
        this.job = run.getParent();
    }

    /**
     * Creates a publisher from job.
     *
     * @param job
     *         a Jenkins job
     */
    public GitHubChecksPublisher(final Job<?, ?> Job) {
        this.job = job;
    }

    /**
     * Publishes a GitHub check.
     *
     * @param details
     *         the details of a check
     */
    @Override
    public void publish(final ChecksDetails details) {
        try {
            GitHubAppCredentials credentials = getGitHubAppCredentials(job);
            GitHub gitHub = Connector.connect(credentials.getApiUri(), gitHubUrl, credentials); // connect to GitHub

            GitHubChecksContext context = new GitHubChecksContext(job); // extract context parameters
            GitHubChecksDetails gitHubDetails = new GitHubChecksDetails(details); // extract checks parameters for GitHub

            publish(gitHub, context, gitHubDetails); // actually publishes the check using thrid party libraries
            LOGGER.log("GitHub check (name: %s, status: %s) has been published.", gitHubDetails.getName(), gitHubDetails.getStatus());
        }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed publishing GitHub checks: " + details, e);
        }
    }

    // ...
}
```

## Checks Parameters

The checks parameters are provided by consumers through the models in the [`api` package](https://github.com/jenkinsci/checks-api-plugin/tree/master/src/main/java/io/jenkins/plugins/checks/api).
You can check the [consumers guide](consumers-guide.md#checks-parameters) for more details.

