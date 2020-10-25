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

For example, when implementing a factory for GitHub checks publisher, you may want to check if some configurations (GitHub App credentials, repository url) are valid before returning the publisher to consumers:
```java
@Extension
public class GitHubChecksPublisherFactory extends ChecksPublisherFactory {
    @Override
    protected Optional<ChecksPublisher> createPublisher(final Run<?, ?> run, final TaskListener listener) {
        GitHubChecksContext context = new GitHubChecksContext(run); // create a context to help extract configurations
        return createPublisher(context, listener);
    }

    @Override
    protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
        GitHubChecksContext context = new GitHubChecksContext(job); // create a context to help extract configurations
        return createPublisher(context, listener);
    }

    private Optional<ChecksPublisher> createPublisher(final GitHubChecksContext context, final TaskListener listener) {
        if (context.isValid()) {
            return Optiona.of(new GitHubChecksPublisher(context, listener));
        }

        return Optional.empty();
    }

    // ...
}
```

## Implement `ChecksPublisher`

After getting the `ChecksPublisher`, the consumers will then invoke the `publish` method and pass their checks parameters in.

By overriding the `publish` method, you should publish checks to the target SCM platform based on the checks parameters or some other context parameters (like repository, branch, commit) from the `Run` or `Job`.

For example, if you want to publish checks to GitHub, you may need a implementation like this:
```java
public class GitHubChecksPublisher extends ChecksPublisher {
    private final TaskListener listener;
    private final GitHubChecksContext context;

    /**
     * Creates a publisher from context.
     *
     * @param context
     *         a context for this check which helps extract repository, branch, commit, credentials, etc.
     */
    public GitHubChecksPublisher(final GitHubChecksContext context) {
        this.context = context;
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
            GitHubAppCredentials credentials = context.getCredentials();
            GitHub gitHub = Connector.connect(credentials.getApiUri(), gitHubUrl, credentials); // connect to GitHub

            GitHubChecksDetails gitHubDetails = new GitHubChecksDetails(details); // extract checks parameters for GitHub

            publish(gitHub, context, gitHubDetails); // actually publishes the check using third party libraries
            listener.getLogger().log("GitHub check (name: %s, status: %s) has been published.", gitHubDetails.getName(), gitHubDetails.getStatus());
        }
        catch (Exception e) {
            listener.getLogger().log(Level.WARNING, "Failed publishing GitHub checks: " + details, e);
        }
    }

    // ...
}
```

## Checks Parameters

The checks parameters are provided by consumers through the models in the [`api` package](https://github.com/jenkinsci/checks-api-plugin/tree/master/src/main/java/io/jenkins/plugins/checks/api).
You can check the [consumers guide](consumers-guide.md#checks-parameters) for more details.

## Check Status

The status checks will be published for three different stages of a build:
- Queued
- Checkout
- Completed

When publishing the checks, this plugin will use the above API as a consumer.

To control the properties of status checks,
you need to implement the interface `StatusChecksProperties`.

There are three methods in this interface:

- `boolean isApplicable(Job<?, ?> job)`

    Implement this method to return `true` if your implementation is not applicable to the `job`.
    
- `String getName(Job<?, ?> job)`

    Implement this method to return the name of the status checks for the `job`.
    
- `boolean isSkip(Job<?, ?> job)`

   Implement this method to return `true` if you want to skip publishing status checks for the `job`.
