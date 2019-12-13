# Micronaut 

[![Build Status](https://github.com/micronaut-projects/micronaut-core/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-core/actions)

Micronaut is a modern, JVM-based, full stack microservices framework designed for building modular, easily testable microservice applications.

Micronaut is developed by the creators of the Grails framework and takes inspiration from lessons learnt over the years building real-world applications from monoliths to microservices using Spring, Spring Boot and Grails.

Micronaut aims to provide all the tools necessary to build microservice applications including:

* Dependency Injection and Inversion of Control (IoC)
* Sensible Defaults and Auto-Configuration
* Configuration and Configuration Sharing
* Service Discovery
* HTTP Routing
* HTTP Client with Client-Side Load Balancing

At the same time Micronaut aims to avoid the downsides of frameworks like Spring, Spring Boot and Grails by providing:

* Fast startup time
* Reduced memory footprint
* Minimal use of reflection
* Minimal use of proxies
* Easy Unit Testing

For more information on using Micronaut see the documentation at [micronaut.io](http://micronaut.io)

## Example Applications

Example Micronaut applications can be found in the [Examples repository](https://github.com/micronaut-projects/micronaut-examples)

## Building From Source

To build from source checkout the code and run:

```
./gradlew publishToMavenLocal
```

This will publish the current version to your local Maven cache. To get the CLI operational you can do:

```
export MICRONAUT_HOME=/path/to/checkout
export PATH="$PATH:$MICRONAUT_HOME/cli/build/bin"
```

You will also need to checkout the [Micronaut Profiles](https://github.com/micronaut-projects/micronaut-profiles/) and run `./gradlew publishToMavenLocal` there too.

You should then be able to `mn create-app hello-world`.

To build the documentation run `./gradlew docs`. The documentation is built to `build/docs/index.html`.

## Contributing Code

If you wish to contribute to the development of Micronaut please read the [CONTRIBUTING.md](CONTRIBUTING.md)

## Versioning

Micronaut is using Semantic Versioning 2.0.0. To understand what that means, please see the specification [documentation](https://semver.org/). Exclusions to Micronaut's public API include any classes annotated with `@Experimental` or `@Internal`, which reside in the `io.micronaut.core.annotation` package.

## CI

[Github Actions](https://github.com/micronaut-projects/micronaut-core/actions) are used to build Micronaut. If a build fails in `master`, check the [test reports](https://micronaut-projects.github.io/micronaut-core/index.html). 




