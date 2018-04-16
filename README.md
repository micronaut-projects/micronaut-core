# Micronaut Framework

## Running Tests

To run the tests use `./gradlew check`

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the documentation run `./gradlew publishGuide` or `./gradlew pG` then open `build/docs/index.html`

## Publish Documentation

Documentation is published with [Travis CI](http://travis-ci.org) with [Github Pages](https://pages.github.com).

Commits to master are published at `snapshot`. 

Commits tagged, for example `v0.0.3`, are published as `latest`, `0.0.x` and `0.0.3`.      