# Contributing Code or Documentation to Micronaut

## Finding Issues to Work on

If you are interested in contributing to Micronaut and are looking for issues to work on, take a look at the issues
tagged with [help wanted](https://github.com/micronaut-projects/micronaut-core/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22).

## JDK Setup

Micronaut currently requires JDK 8

## IDE Setup

Micronaut can be imported into IntelliJ IDEA by opening the `build.gradle` file.

## Docker Setup

Micronaut tests currently require docker to be installed.

## Running Tests

To run the tests use `./gradlew check`.

[Geb](http://gebish.org) functional tests are ignored unless you specify the geb environment via system property.

To run with Chrome `./gradlew -Dgeb.env=chrome check`.

To run with Firefox `./gradlew -Dgeb.env=firefox check`.

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the documentation run `./gradlew publishGuide` or `./gradlew pG` then open `build/docs/index.html`

To also build the javadocs instead run `./gradlew docs`.

## Building the CLI

You can build the CLI from source by [following these instructions](https://micronaut-projects.github.io/micronaut-starter/latest/guide/index.html#installFromSource)

## Working on the code base

If you are working with the IntelliJ IDEA development environment, you can import the project using the Intellij Gradle
Tooling ( "File / Import Project" and select the "settings.gradle" file).

The most important command you will have to run before sending your changes is the check command.

./gradlew check

For a successful contribution, all tests should be green!

## Creating a pull request

Once you are satisfied with your changes:

- Commit your changes in your local branch
- Push your changes to your remote branch on GitHub
- Send us a [pull request](https://help.github.com/articles/creating-a-pull-request)

## Checkstyle

We want to keep the code clean, following good practices about organization, javadoc and style as much as possible.

Micronaut uses [Checkstyle](http://checkstyle.sourceforge.net/) to make sure that all the code follows those standards. The configuration file is defined in `config/checkstyle/checkstyle.xml` and to execute Checkstyle you
need to run:

```
./gradlew <module-name>:checkstyleMain
```

Before you start contributing with new code it is recommended that you install the IntelliJ [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin and configure it to use Micronaut's Checkstyle configuration file.

IntelliJ will mark the issues Checkstyle finds in red. For example:

![](https://raw.githubusercontent.com/micronaut-projects/micronaut-core/5e4be034d92e5c3932967039eb6d665dccead1ab/src/main/docs/resources/img/checkstyle-issue.png)

In this case, to fix the issues, we need to:

- Add one empty line before `package` in line 16
- Add the Javadoc for the constructor in line 27
- Add a space after `if` in line 34

The plugin also adds a new tab at the bottom to run the Checkstyle report and display all the errors and warnings. It is recommended
that you run the report and fix any issues before submitting a pull request.

## Building on Windows 10

The following prerequisites are needed for building and testing on Windows 10:

* Docker Desktop version 2.0.0.0 win81 build 29211 or higher is installed and running.
* OpenSSL binaries are installed, for example (https://indy.fulgan.com/SSL/) and on the PATH.
