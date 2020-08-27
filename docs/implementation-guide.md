# Implementation Guide

This document guides you how to implement this checks API for a specific remote SCM platform.

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

## Implement `ChecksPublisher`

After getting the `ChecksPublisher`, the consumers will then invoke the `publish` method and pass their checks parameters in.

By overriding the `publish` method, you should publish checks to the target SCM platform based on the checks parameters or some other context parameters (like repository, branch, commit) from the `Run` or `Job`.

## Checks Parameters

The checks parameters are provided by consumers through the models in the [`api` package](https://github.com/jenkinsci/checks-api-plugin/tree/master/src/main/java/io/jenkins/plugins/checks/api).
You can check the [consumers guide](consumers-guide.md#checks-parameters) for more details.

