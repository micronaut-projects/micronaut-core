# Generated annotation maps: core prototype

This branch moves the typed-map experiment into `core`, `inject`, `core-processor`,
and the Java annotation processor. It is opt-in and experimental. The public
addition is `@GenerateAnnotationMap` (`@since 5.2.3`, matching the target branch).
The runtime base, factory used by generated metadata, and processor are internal.
Existing method signatures remain unchanged; the API addition is minor-version compatible.

## Use

```java
@GenerateAnnotationMap
@Retention(RetentionPolicy.RUNTIME)
public @interface Rule {
    String name() default "default";
    int count() default 7;
}
```

With `micronaut-inject-java` on the annotation processor path, this generates
`Rule$AnnotationMap`, `Rule$AnnotationMapAdapter`, and `Rule$AnnotationMapStored`.
The interface extends `Map<CharSequence, Object>`; it is separate from the Java
annotation interface.

```java
Rule$AnnotationMap rule = Rule$AnnotationMap.of(
    metadata.getValues(Rule.class.getName())
);
String name = rule.name();
int count = rule.count();
```

The compiler stores eligible member maps as immutable generated instances.
`DefaultAnnotationMetadata.getValues` and `AnnotationValue.getValues` preserve
these instances, so `of(map)` returns the same object and getters read final
fields. `AnnotationMap` delegates every Map operation, including Java default
methods, equality, and collection views. Those methods are final, and immutable
instances freeze their backing map before initializing cached fields.

For an ordinary map, `of(map)` returns a live adapter that uses the existing
`AnnotationValue` conversion semantics. Mutations through either the original map
or the adapter remain visible. `immutable(map)` explicitly snapshots a map whose
known members are already String/Integer values. Unknown entries are retained;
missing entries remain absent even when typed accessors return annotation defaults.
`of(map)` does not check whether the annotation itself is present; use a separate
presence check when needed. A member such as `size` gets the accessor `member_size()` to preserve Map semantics.

## Scope and fallback

- Generation supports top-level Java annotations with String/int members and
  literal defaults. Unsupported member types, missing/dynamic defaults, nested
  declarations, and accessor collisions produce compilation errors.
- Direct annotations, stereotypes, introspection properties, and executable
  argument metadata can use typed storage. Class/method hierarchy merges still
  use the existing merge logic; `of(map)` adapts the merged result and preserves
  member precedence.
- Metadata containing property placeholders or evaluated expressions keeps its
  existing representation. Resolve such values through the normal metadata APIs;
  constructing a typed view of a raw map does not evaluate expressions.
- The generated `create(Map)` factory checks known raw member types and falls back
  to the original map for values that require conversion. It is a compiler entry
  point, not the recommended application factory.
- Java generation and Java consumption are implemented. Groovy/Kotlin generation,
  nested/repeatable value specialization, retained-stereotype filtering, schema
  evolution across separately compiled versions, and native-image behavior are
  outside this prototype's verified scope.
- Library authors must publish the generated companions with the annotation.
  Compiling an opted-in annotation without its processor is unsupported.

The prototype adds an object and cached fields per stored member map, plus three
classes per schema. Class loading, initialization, startup, and retained heap need
separate evaluation before enabling this for common framework annotations.

## Benchmark

`GeneratedAnnotationMapBenchmark` reads two members from 16 distinct property
metadata instances emitted by the real introspection processor. Setup asserts
that typed storage was installed. The baseline copies all annotation and stereotype maps, including nullability
annotations, into ordinary `DefaultAnnotationMetadata` using the same immutable
map shapes; the other cases measure existing metadata
APIs on typed storage, typed getters, and the adapter fallback. Every operation
rotates its input and returns a value derived from both members.

```sh
./gradlew :benchmarks:jmh \
  -Pjmh.includes=io.micronaut.benchmark.metadata.GeneratedAnnotationMapBenchmark \
  -Pjmh.fork=3 -Pjmh.warmupIterations=3 -Pjmh.iterations=5 \
  -Pjmh.warmupTime=500ms -Pjmh.timeOnIteration=500ms -Pjmh.profilers=gc
```

Measured on 2026-09-17, macOS arm64, OpenJDK 25.0.2, JMH 1.37. Three forks,
three 500 ms warmups and five 500 ms measurements per fork, one thread,
`-prof gc`. Errors are JMH's 99.9% confidence intervals. These are exploratory
local measurements on a development machine, not application-level speedups.

| Two-member read, including annotation lookup | ns/op |
| --- | ---: |
| Ordinary metadata, existing scalar APIs | 8.600 ± 0.027 |
| Generated storage, existing scalar APIs | 9.216 ± 0.332 |
| Generated storage, typed `of(map)` getters | 3.020 ± 0.049 |
| Ordinary metadata, live `of(map)` adapter | 7.737 ± 0.043 |

Typed reads took about **65% less time** than the ordinary metadata baseline.
Keeping the old scalar APIs on generated storage cost about **7% more time**.
All four inlined read benchmarks measured approximately zero allocation
(~0.0001 B/op profiler noise); this does not imply that escaping adapters or
snapshot construction are allocation-free. Construction is outside the timed loop.
The [raw JMH summary](generated-annotation-map-jmh.txt) contains all GC metrics.

The recommended next step is a single real consumer migration to typed getters,
with startup, retained heap, class-size, and end-to-end measurements. Keep storage
opt-in until that demonstrates a worthwhile overall gain. Extending member-type
support, especially annotation class values and arrays, is needed before trying
many common validation/serialization annotations.

## Implementation map

- `core/.../annotation/GenerateAnnotationMap.java`: experimental opt-in annotation.
- `core/.../annotation/AnnotationMap.java`: map delegation, snapshot ownership, and
  read-only boundary helper. `AnnotationValue` uses that helper; `AnnotationUtil`
  excludes the generator hint from application metadata.
- `inject/.../annotation/DefaultAnnotationMetadata.java`: preserves immutable typed
  maps at the public read boundary. `MutableAnnotationMetadata` carries the
  compile-time opt-in set through clones and metadata contributions.
- `core-processor/.../annotation/AbstractAnnotationMetadataBuilder.java` and
  `AnnotationMetadataGenUtils.java`: record eligible schemas and emit the factory
  calls when values are static.
- `inject-java/.../processing/AnnotationMapProcessor.java`: isolating Java processor
  using Sourcegen models and the existing Micronaut bytecode writer. Native Java
  annotation declarations are processed directly because normal type visitors
  omit annotation declarations. `JavaAnnotationMetadataBuilder`, the processor
  service registrations, and the test parser connect it to the compilation path.
- `AnnotationMapSpec` and `GeneratedAnnotationMapSpec`: runtime immutability and
  real compiler/context integration tests.
- `LookupRule`, `LookupFixture`, and `GeneratedAnnotationMapBenchmark`: the
  integrated benchmark and its generated metadata inputs.

## Verification

On the prototype branch:

- Focused tests passed: 18 runtime contract cases in `AnnotationMapSpec` and 10
  compiler/context cases in `GeneratedAnnotationMapSpec`.
- `:micronaut-core:check`, `:micronaut-inject:check`, and
  `:micronaut-core-processor:check` completed successfully. The subsequent broad
  run passed 7,442 core tests, 410 inject tests, 5,338 Java compiler tests,
  1,142 Groovy compiler tests, and 896 Kotlin compiler tests (counts include skipped
  cases). Groovy/Kotlin checks verify existing behavior, not typed-map generation.
- Binary compatibility tasks completed for `core`, `inject`, `core-processor`,
  `inject-java`, and `inject-java-test` during `./gradlew check`.
- The broad `check` run did **not** complete cleanly. It recorded connection
  failures in `PathVariableSpec` and `TxtPlainBigDecimalTest` and certificate reload
  failures in two `FileCertificateProviderTest` cases, then stalled waiting for a
  test worker to stop. The run was interrupted. All four failed cases passed when
  their three test classes were rerun with `--no-parallel --max-workers=2`.
- `./gradlew docs cM spotlessCheck` completed successfully, together with the
  isolated HTTP reruns.
- Existing Checkstyle diagnostics are present in untouched
  `IntrospectedTypeElementVisitor`, `PyronautJavaCompiler`, `GroovyPropertyElement`,
  and `NettyWebSocketSession`. Existing Javadoc diagnostics also occur in untouched
  code. These tasks are configured to tolerate those diagnostics.
