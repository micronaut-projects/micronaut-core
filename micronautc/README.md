# Micronautc

This module builds the `micronautc` native executable and includes a Profile-Guided Optimization (PGO) workflow for GraalVM native-image.

## Generate a PGO Profile

Run the training workflow task:

```bash
./gradlew :micronaut-micronautc:generateMicronautcPgoProfile
```

This task chain:

1. Builds an instrumented `micronautc` native executable.
2. Runs `micronautc` against representative training sources (bean definition + introspection scenarios).
3. Copies the generated profile to:

```text
micronautc/src/pgo-profiles/main/default.iprof
```

## Build Using the PGO Profile

Once `default.iprof` exists, run:

```bash
./gradlew :micronaut-micronautc:nativeCompile
```

The native-image build automatically consumes profiles from `src/pgo-profiles/main`.
In native-image output you should see `PGO: user-provided`.

## Optional: Force Instrumented Build Mode

You can force instrumentation mode directly with:

```bash
./gradlew :micronaut-micronautc:nativeCompile -Pmicronautc.pgo.instrument=true
```

## Notes

- PGO support requires Oracle GraalVM (not GraalVM Community Edition).
- If no `.iprof` profile is present, `nativeCompile` falls back to the default non-PGO build configuration.
