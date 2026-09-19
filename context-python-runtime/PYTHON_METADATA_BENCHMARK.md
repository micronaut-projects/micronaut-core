# Python metadata backends: baseline measurements

Measured with `PythonMetadataBenchmarkSpec` (inject-python-test), opt-in:

```sh
JAVA_HOME=<jdk-25> ./gradlew :micronaut-inject-python-test:test --tests '*PythonMetadataBenchmarkSpec' -Ppython-ci \
  -Dpython.metadata.benchmark=true -Dpython.metadata.benchmark.modules=40 \
  -Dpython.metadata.benchmark.compileRounds=3 -Dpython.metadata.benchmark.startupRounds=3 \
  -Dpython.metadata.benchmark.report=<directory>
```

The fixture is 40 Python modules, each with two singleton beans (one with constructor injection of the other and a
`@Value` placeholder) and one introspected class with three properties: 80 bean definitions and 40 introspections,
next to the 135 framework definitions of the application (255 definitions in total). Every round compiles into a
fresh directory with a fresh compiler, and every application run is a fresh JVM without `jdk.compiler`, using the
class path of the separate-JVM test (the Python runtime and this module, neither compiler). Backends alternate
within every round. The `few` scenario resolves one bean and one introspection; `many` resolves every declared
bean and introspection. Both scenarios ask the context for all bean definitions once, which loads (and, in the
runtime backend, generates) every definition; the generated-class counts include them.

Commit `604b891aa0bf4b8aff5d409840e1d062e5d6292c` (this branch), Sourcegen 2.1.0, ASM 9.10.1, Gradle 9.7.1, the
Micronaut version below is the one the built runtime reports. The Truffle JIT could not be measured: this JDK is
not a GraalVM and the Graal compiler is not on the class path, so every run uses GraalPy's fallback interpreter.

## Reading the numbers

- Compilation time is the same for the three backends: the medians are within 0.3 s of each other for a 4.5 s
  compilation, and the spread of a backend is larger than the difference between backends (the compiler backend's
  15 s outlier is the first compilation of the session, before the worker warmed up). Emitting the metadata
  classes is not where the Python compiler spends its time.
- The runtime backend ships 644 KB fewer class bytes (no definition or introspection classes, no 120 service
  entries) and 140 KB of models and catalog instead. The build-time model backend's classes are 40% smaller than
  the compiler's (they initialize from the model instead of carrying the metadata as bytecode) but its output also
  carries the models.
- Context start (3.9 s, dominated by creating the GraalPy context and its first import) and first bean (3.0 s,
  the Python side of the first bean) do not move. Generating 80 definitions during context start costs nothing
  measurable: the runtime backend's context start is inside the spread of the other two.
- The first introspection is faster in the runtime backend (6 ms against 21 ms and 35 ms): materializing one
  model and defining one class is cheaper than initializing the compiler's introspection class. Resolving all 40
  introspections is slower (65 ms against 29 ms and 19 ms): about 1.6 ms of generation per class against class
  loading. Neither is visible next to the Python costs.
- Retained heap after GC differs by about 1 MB (42.5 MB against 41.4 MB) with 120 generated classes, and the
  loaded-class count by about 30 classes (the runtime module and ASM).

No startup or memory benefit is claimed from these runs; the benefits established are the smaller packaged
output and the removal of the metadata classes from the build, at no measurable cost. The plan's other
performance workstreams (Python transformation, wrapper reduction, target-type mapping consolidation) are where
the compilation time is.

# Python metadata backends: measurements

- java: OpenJDK 64-Bit Server VM 25.0.4.1+1-LTS
- os: Linux amd64
- cpus: 4
- commit: 604b891aa0bf4b8aff5d409840e1d062e5d6292c
- graalpy: 25.3.4.1
- pythonRuntime: GraalPy fallback interpreter (no Truffle JIT on this JDK: the Graal compiler is not on the class path)
- micronaut: 5.2.3-SNAPSHOT
- fixture: [modules:40, beans:80, introspections:40]

## Compilation (ms, 3 alternating rounds per backend)

| backend | median | min | max |
|---|---:|---:|---:|
| compiler | 4530.3 | 4447.8 | 15163.4 |
| model-build-time | 4856.6 | 4274.1 | 6070.2 |
| model-runtime | 4557.6 | 4326.8 | 4977.7 |

## Output inventory

| backend | kind | files | bytes |
|---|---|---:|---:|
| compiler | definitions | 80 | 367848 |
| compiler | introspections | 40 | 276373 |
| compiler | pythonResources | 84 | 392264 |
| compiler | serviceEntries | 120 | 0 |
| compiler | targetTypeMappings | 600 | 685941 |
| compiler | wrapperClasses | 121 | 540901 |
| compiler | wrapperSources | 120 | 634656 |
| model-build-time | definitions | 80 | 248448 |
| model-build-time | introspections | 40 | 123533 |
| model-build-time | models | 120 | 131269 |
| model-build-time | pythonResources | 84 | 392264 |
| model-build-time | serviceEntries | 120 | 0 |
| model-build-time | targetTypeMappings | 600 | 685941 |
| model-build-time | wrapperClasses | 121 | 540901 |
| model-build-time | wrapperSources | 120 | 634656 |
| model-runtime | catalog | 1 | 9450 |
| model-runtime | models | 120 | 131269 |
| model-runtime | pythonResources | 84 | 392264 |
| model-runtime | targetTypeMappings | 600 | 685941 |
| model-runtime | wrapperClasses | 121 | 540901 |
| model-runtime | wrapperSources | 120 | 634656 |

## Startup and first use in a fresh JVM (ms, medians; fallback interpreter)

### Scenario: few

| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| compiler | 3900.3 | 3001.9 | 0.0 | 21.2 | 3.1 | 1448.9 | 0.0 | 21868.0 | 41.4 | 10722.0 |
| model-build-time | 4012.7 | 2942.9 | 0.0 | 35.3 | 0.9 | 1419.8 | 0.0 | 21901.0 | 42.3 | 10694.0 |
| model-runtime | 3883.6 | 2943.2 | 0.0 | 6.6 | 0.4 | 1699.0 | 81.0 | 21900.0 | 41.9 | 10829.0 |

### Scenario: many

| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| compiler | 3923.0 | 3020.5 | 67.4 | 22.5 | 28.6 | 1383.7 | 0.0 | 21869.0 | 41.4 | 10817.0 |
| model-build-time | 3894.2 | 2891.0 | 67.4 | 30.9 | 19.0 | 1719.4 | 0.0 | 21899.0 | 42.4 | 11198.0 |
| model-runtime | 3867.3 | 3074.2 | 66.8 | 6.0 | 64.7 | 1488.0 | 120.0 | 21937.0 | 42.5 | 10895.0 |

