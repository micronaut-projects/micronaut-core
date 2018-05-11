# Micronaut Framework

## Running Tests

To run the tests use `./gradlew check`

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the documentation run `./gradlew publishGuide` or `./gradlew pG` then open `build/docs/index.html`  

## How to run Micronaut CLI

- Clone [Micronaut Profiles](https://github.com/micronaut-projects/micronaut-profiles)
- Install micronaut-profiles to Maven Local `micronaut-profiles$ ./gradlew clean publishToMavenLocal`
- `micronaut-core$ ./gradlew cli:fatJar`
- `micronaut-core$ cd cli/build/bin`
- `micronaut-core/cli/build/bin$ ./mn`

## Developer Guide ##

We want to keep the code clean, following good practices about organization, javadoc and style as much homogeneous as
possible. Micronaut uses [Checkstyle](http://checkstyle.sourceforge.net/) to make sure that all the code follows those
standards. The configuration file is defined in `config/checkstyle/checkstyle.xml` and to execute the Checkstyle you
need to run:
 
```
./gradlew <module-name>:checkstyleMain
```

Before start contributing with new code it is recommended to install IntelliJ *checkstyle-IDEA* plugin and configure it 
to use Micronaut's checkstyle configuration file.  
Now IntelliJ will mark in red the issues.

![](https://github.com/micronaut-projects/micronaut-core/raw/master/src/main/docs/resources/img/checkstyle-issue.png)

In this case, to fix the issues, we need to:
- Add one empty line before `package` in line 16
- Add the Javadoc for the constructor in line 27
- Add an space after `if` in line 34

This is the fastest way to see the checkstyle issues as we modify or create new files.

The plugin also adds a new tab in the bottom to run checkstyle report and see all the errors and warnings. It is recommended
to run the report and fixing all the issues before submitting a pull request.

