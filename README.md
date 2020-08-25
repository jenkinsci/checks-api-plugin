# Checks API Plugin
[![Join the chat at https://gitter.im/jenkinsci/github-checks-api](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jenkinsci/github-checks-api)
[![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://issues.jenkins-ci.org/issues/?jql=component%20%3D%20checks-api-plugin)
[![Jenkins](https://ci.jenkins.io/job/Plugins/job/checks-api-plugin/job/master/badge/icon?subject=Jenkins%20CI)](https://ci.jenkins.io/job/Plugins/job/checks-api-plugin/job/master/)
[![GitHub Actions](https://github.com/jenkinsci/checks-api-plugin/workflows/CI/badge.svg?branch=master)](https://github.com/jenkinsci/checks-api-plugin/actions)
[![codecov](https://codecov.io/gh/jenkinsci/checks-api-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/jenkinsci/checks-api-plugin)

Inspired by the [GitHub Checks API](https://docs.github.com/en/rest/reference/checks#runs), this plugin aims to provide a general API to allow Jenkins plugins publishing checks (or reports) to remote source code management (SCM) platforms (e.g. GitHub, GitLab, BitBucket, etc.).
By consuming this API, other plugins can publish check with customized parameters for a Jenkins build, such as status, summary, warnings, code annotations, or even images.
Then, the implementations of this API will decide on how to make use of these parameters and where to publish the checks.

Current consumers of this plugin include [Warnings Next Generation Plugin](https://github.com/jenkinsci/warnings-ng-plugin) and [Code Coverage API Plugin](https://github.com/jenkinsci/code-coverage-api-plugin); current implementations include [GitHub Checks Plugin](https://github.com/jenkinsci/github-checks-plugin).

# Consumers Guide

## A Simple Example

Imagine that you want to publish a check with some details of a Jenkins build. 
You need to first construct a `ChecksDetails` object:

```
ChecksDetails details = new ChecksDetailsBuilder()
 .withName("Jenkins CI")
 .withStatus(ChecksStatus.COMPLETED)
 .withConclusion(ChecksConclusion.SUCCESS)
 .withDetailsURL("https://ci.jenkins.io")
 .withCompletedAt(LocalDateTime.now(ZoneOffset.UTC))
 .build();
```

Then you can create a publisher based on a Jenkins `run` to publish the check you just constructed:

```
ChecksPublisher publisher = ChecksPublisher.fromRun(run);
publisher.publish(details);
```

The publisher returned is based on the implementations you installed on your Jenkins instance.

## Checks Parameters

The checks are highly customized by consumers due to a number of optional parameters provided.
Consumers can set these parameters through the checks models:

- `ChecksDetails`: the top-level model of a check, including all other models and some basic parameters like status and conclusion;
- `ChecksOutput`: the output of a check, providing some displayed details like title, summary, URL, and code annotations;
- `ChecksAnnotation`: a code annotation of a check, providing a specific comment for one or multiple lines of code;
- `ChecksImage`: an image of a check, providing an intuitive graph for the build like issues trend, coverage chart;
- `ChecksAction`: an action of a check, providing further actions to be performed by users, like rerun a Jenkins build; 

## Checks Publishers

The publishers are created through the static factory method (`fromRun` or `fromJob`) of `ChecksPublisherFactory`.
The factory will iterate all available implementations of the `ChecksPublisher` in order to find the suitable publisher for the Jenkins `Run` or `Job`.

# Embedded Features

## Build Status Check

![GitHub Status](docs/images/github-status.png)

By listening to the Jenkins builds, this plugin will automatically publish statuses (pending, in progress, and completed) to different SCM platforms based on the remote repository the build is using.

## Pipeline Usage

Instead of depending on consumers plugins, the users can publish their checks directly in the pipeline script:

```
publishChecks name: 'example', title: 'Pipeline Check', summary: 'check through pipeline', text: 'you can publish checks in pipeline script', detailsURL: 'https://github.com/jenkinsci/checks-api-plugin#pipeline-usage'
```

# Acknowledgements

This plugin is started as a [Google Summer of Code 2020 project](https://summerofcode.withgoogle.com/projects/#5139745388101632), you can find more about it on [Jenkins GSoC SIG](https://www.jenkins.io/sigs/gsoc/).
