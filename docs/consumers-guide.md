
# Consumers Guide

## A Simple Example

Imagine that you want to publish a check with some details of a Jenkins build. 

You need to first construct a `ChecksDetails` object:

```
ChecksDetails details = new ChecksDetailsBuilder()
        .withName("Jenkins CI")
        .withStatus(ChecksStatus.COMPLETED)
        .withConclusion(ChecksConclusion.SUCCESS)
        .withDetailsURL(DisplayURLProvider.get().getRunURL(run))
        .withCompletedAt(LocalDateTime.now(ZoneOffset.UTC))
        .build();
```

Then you can create a publisher based on a Jenkins `Run` to publish the check you just constructed:

```
ChecksPublisher publisher = ChecksPublisher.fromRun(run);
publisher.publish(details);
```

The publisher returned is based on the implementations you installed on your Jenkins instance.

## Checks Parameters

The checks are highly customized by consumers due to a number of optional parameters provided.

Consumers can set these parameters through the checks models:

- `ChecksDetails`: the top-level model of a check, including all other models and some basic parameters like status and conclusion
- `ChecksOutput`: the output of a check, providing some displayed details like title, summary, URL, and code annotations
- `ChecksAnnotation`: a code annotation of a check, providing a specific comment for one or multiple lines of code;
- `ChecksImage`: an image of a check, providing an intuitive graph for the build like issues trend, coverage chart, must be a public URL that SCM platforms can fetch from
- `ChecksAction`: an action of a check, providing further actions to be performed by users, like rerun a Jenkins build

## Checks Publishers

The publishers are created through the static factory method (`fromRun` or `fromJob`) of `ChecksPublisherFactory`.
The factory will iterate all available implementations of the `ChecksPublisher` in order to find the suitable publisher for the Jenkins `Run` or `Job`.

## Pipeline Step: withChecks

The `withChecks` step injects a `ChecksInfo` object into its closure by users:

```groovy
withChecks('MyCheck') {
  junit '*.xml'
}
```

The injected object can be resolved by other plugin developers in their [Step](https://javadoc.jenkins.io/plugin/workflow-step-api/org/jenkinsci/plugins/workflow/steps/Step.html) implementation:

```
getContext().get(ChecksInfo.class)
```

Currently, the `ChecksInfo` object only includes a `name` specified by users,
it is recommended that you look for this name and set it over your default checks name

## Integration Testing

An implementation of `ChecksPublisher` that captures all published `ChecksDetails` is provided
in the `test` classifier, as `io.jenkins.plugins.checks.api.test.CapturingChecksPublisher`.

Adding the factory for this publisher as a `TestExtension` will allow inspection of published checks after running a job
on a `JenkinsRule`.