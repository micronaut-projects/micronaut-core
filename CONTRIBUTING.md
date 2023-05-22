# Contributing Code or Documentation to the Micronaut Framework

## Finding Issues to Work on

If you are interested in contributing to the Micronaut Framework and are looking for issues to work on, take a look at the issues tagged with [help wanted](https://github.com/micronaut-projects/micronaut-core/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22).

## JDK Setup

The Micronaut Framework currently requires JDK 8

## IDE Setup

The Micronaut Framework project is imported into IntelliJ IDEA by opening the `build.gradle` file.

## Docker Setup

The Micronaut Framework tests require Docker.

## Running Tests

To run the tests use `./gradlew check`.

[Geb](http://gebish.org) functional tests are ignored unless you specify the geb environment via system property.

To run with Chrome `./gradlew -Dgeb.env=chrome check`.

To run with Firefox `./gradlew -Dgeb.env=firefox check`.

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the documentation run `./gradlew publishGuide` or `./gradlew pG` then open `build/working/02-docs-raw/index.html`
> This only generates the raw guide without the API and Configuration references; therefore, the API links in the manual
> will not resolve in a browser.

To include the API (javadocs) and Configuration references run `./gradlew docs` instead and open `build/docs/index.html`


## Working on the code base

If you are working with the IntelliJ IDEA development environment, you can import the project using Intellij's Gradle Tooling ("File / Open..." and select the "build.gradle" file or the project directory).

Create a branch from the release branch to which you anticipate merging back changes, e.g. `3.4.x`, `3.5.x`, `3.6.x`, etc.

The most important task to complete before submitting work is the `check` task. This executes all the unit tests as well as various code quality checks.

```
./gradlew check
```

The `check` task should complete successfully. Otherwise, the initial pull request will fail, and you will need to make corrections before it can be reviewed (unless you are opening a draft pull request).

## Creating a pull request

Once you are satisfied with your changes:

- Commit changes to the local branch you created.
- Push that branch with changes to the corresponding remote branch on GitHub
- Submit a [pull request](https://help.github.com/articles/creating-a-pull-request)

## Checkstyle

The code base should remain clean, following industry best practices for organization, javadoc and style, as much as possible.

The Micronaut Framework uses [Checkstyle](http://checkstyle.sourceforge.net/) to make sure that all the code follows those standards. The configuration file is defined in `config/checkstyle/checkstyle.xml`.
To execute the Checkstyle task run:

```
./gradlew <module-name>:checkstyleMain
```

Before contributing new code it is recommended you install IntelliJ [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin and configure it to use Micronaut Framework's checkstyle configuration file.

IntelliJ will mark in red the issues Checkstyle finds. For example:

![checkstyle-issue](https://docs.micronaut.io/docsassets/img/checkstyle-issue.png)

In this case, to fix the issues, we need to:

- Add one empty line before `package` in line 16
- Add the Javadoc for the constructor in line 27
- Add a space after `if` in line 34

The plugin also adds a new tab in IDEA's bottom view pane to run a checkstyle report to display errors and warnings.
Run the report and fix any exposed issues before submitting a pull request. The gradle `check` task also produces a HTML report if there are errors.

## Building on Windows 10

The following prerequisites are needed for building and testing on Windows 10:

* Docker Desktop version 2.0.0.0 win81 build 29211 or higher is installed and running.
* OpenSSL binaries are installed, for example (https://indy.fulgan.com/SSL/) and on the PATH.
