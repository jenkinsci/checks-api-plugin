# Checks API Plugin
[![Join the chat at https://gitter.im/jenkinsci/github-checks-api](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jenkinsci/github-checks-api)
[![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://issues.jenkins-ci.org/issues/?jql=component%20%3D%20checks-api-plugin)
[![Jenkins](https://ci.jenkins.io/job/Plugins/job/checks-api-plugin/job/master/badge/icon?subject=Jenkins%20CI)](https://ci.jenkins.io/job/Plugins/job/checks-api-plugin/job/master/)
[![GitHub Actions](https://github.com/jenkinsci/checks-api-plugin/workflows/CI/badge.svg?branch=master)](https://github.com/jenkinsci/checks-api-plugin/actions)
[![codecov](https://codecov.io/gh/jenkinsci/checks-api-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/jenkinsci/checks-api-plugin)

Inspired by the [GitHub Checks API](https://docs.github.com/en/rest/reference/checks#runs), this plugin aims to provide a general API to allow Jenkins plugins publishing checks (or reports) to remote source code management (SCM) platforms (e.g. GitHub, GitLab, BitBucket, etc.).

By consuming this API, other plugins can publish check with customized parameters for a Jenkins build, such as status, summary, warnings, code annotations, or even images.
Implementations of this API decide on how to make use of these parameters and where to publish the checks.

Known consumers:
* [Warnings Next Generation Plugin](https://plugins.jenkins.io/warnings-ng)
* [Code Coverage API Plugin](https://plugins.jenkins.io/code-coverage-api)
* [JUnit Plugin](https://plugins.jenkins.io/junit/) - since 1.39
* [xUnit Plugin](https://plugins.jenkins.io/xunit) - since 3.1.0
* [Autograding Plugin](https://plugins.jenkins.io/autograding)

Implementations:
* [GitHub Checks Plugin](https://plugins.jenkins.io/github-checks)
* [Gitea Checks Plugin](https://plugins.jenkins.io/gitea-checks/)

This plugin, along with it's [GitHub implementation](https://plugins.jenkins.io/github-checks), has been installed on [ci.jenkins.io](https://ci.jenkins.io/Plugins) to help maintain over 1500 Jenkins plugins. You can take a look at the [action](https://github.com/jenkinsci/checks-api-plugin/runs/1025532156) for this repository or other plugin repositories under [Jenkins organization](https://github.com/jenkinsci) for the results.

## Embedded Features

### Build Status Check

![GitHub Status](docs/images/github-status.png)

This plugin defines extension points to publish statuses to different SCM platforms.

It depends on the implementation to decide whether to skip them and what name to use.

If enabled, the statuses will be published in different stages of a Jenkins build (enters the queue, checkout, and completes).

### Pipeline Usage

- publishChecks: you can publish checks directly in the pipeline script instead of depending on consumer plugins:

```
publishChecks name: 'example', title: 'Pipeline Check', summary: 'check through pipeline',
    text: 'you can publish checks in pipeline script',
    detailsURL: 'https://github.com/jenkinsci/checks-api-plugin#pipeline-usage',
    actions: [[label:'an-user-request-action', description:'actions allow users to request pre-defined behaviours', identifier:'an unique identifier']]
```

*To use customized actions, you will need to write a Jenkins plugin
If you want to add GitHub checks actions which are basically buttons on the checks report,
you need to extend [GHEventSubscriber](https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github/extension/GHEventsSubscriber.java) to handle the event,
see [the handler](https://github.com/jenkinsci/github-checks-plugin/blob/ea060be67dad522ab6c31444fc4274955ac6e918/src/main/java/io/jenkins/plugins/checks/github/CheckRunGHEventSubscriber.java) for re-run requests as an example.*

- withChecks: you can inject the check's name into the closure for other steps to use:

```
withChecks(name: 'injected name') {
    // some other steps that will extract the name
}
```

## Guides

- [Consumers Guide](docs/consumers-guide.md)
- [Implementation Guide](docs/implementation-guide.md)

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## Acknowledgements

This plugin was started as a [Google Summer of Code 2020 project](https://summerofcode.withgoogle.com/projects/#5139745388101632), special thanks to the support from [Jenkins GSoC SIG](https://www.jenkins.io/sigs/gsoc/) and the entire community.


## LICENSE

Licensed under MIT, see [LICENSE](LICENSE)
