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
 
  
 
