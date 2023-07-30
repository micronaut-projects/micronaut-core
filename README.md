# Micronaut Framework

[![Build Status](https://github.com/micronaut-projects/micronaut-core/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-core/actions)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=micronaut-projects_micronaut-core&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=micronaut-projects_micronaut-core)

[Micronaut Framework](https://micronaut.io) is a modern, JVM-based, full stack Java framework designed for building modular, easily testable JVM applications with support for Java, Kotlin and the Groovy language.

Micronaut Framework is developed by the creators of the Grails framework and takes inspiration from lessons learnt over the years building real-world applications from monoliths to microservices using Spring, Spring Boot and Grails.

Micronaut Framework aims to provide all the tools necessary to build JVM applications including:

* Dependency Injection and Inversion of Control (IoC)
* Aspect Oriented Programming (AOP)
* Sensible Defaults and Auto-Configuration

With Micronaut Framework you can build Message-Driven Applications, Command Line Applications, HTTP Servers and more whilst for Microservices in particular Micronaut Framework also provides:

* Distributed Configuration
* Service Discovery
* HTTP Routing
* Client-Side Load Balancing

At the same time Micronaut Framework aims to avoid the downsides of frameworks like Spring, Spring Boot and Grails by providing:

* Fast startup time
* Reduced memory footprint
* Minimal use of reflection
* Minimal use of proxies
* No runtime bytecode generation
* Easy Unit Testing

This is achieved by pre-computing the framework infrastructure at compilation time which reduces the logic required at runtime for the application to work.

For more information on using Micronaut Framework see the documentation at [micronaut.io](https://micronaut.io)

## Example Applications

Example Micronaut Framework applications can be found in the [Examples repository](https://github.com/micronaut-projects/micronaut-examples)

## Building From Source

To build from source checkout the code and run:

```
./gradlew publishToMavenLocal
```

To build the documentation run `./gradlew docs`. The documentation is built to `build/docs/index.html`.

## Contributing Code

If you wish to contribute to the development of Micronaut Framework please read the [CONTRIBUTING.md](CONTRIBUTING.md)

## Versioning

Micronaut Framework is using Semantic Versioning 2.0.0. To understand what that means, please see the specification [documentation](https://semver.org/). Exclusions to Micronaut Framework's public API include any classes annotated with `@Experimental` or `@Internal`, which reside in the `io.micronaut.core.annotation` package.

## CI

[GitHub Actions](https://github.com/micronaut-projects/micronaut-core/actions) are used to build Micronaut Framework. If a build fails in `master`, check the [test reports](https://micronaut-projects.github.io/micronaut-core/index.html).




