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

Commit `f3aecece839c4717d4b00da4a47c7c7e3e14f702` (this branch), Sourcegen 2.1.0, ASM 9.10.1, Gradle 9.7.1, the
Micronaut version below is the one the built runtime reports. The Truffle JIT could not be measured: this JDK is
not a GraalVM and the Graal compiler is not on the class path, so every run uses GraalPy's fallback interpreter.

## Reading the numbers

- Compilation time is the same for the three backends within the noise of this machine: the compiler and runtime
  medians are 5.3 s and 5.2 s, and every backend's own spread (5.0 s to 22.7 s) dwarfs the difference between
  them, first compilations of a session included. Emitting the metadata classes is not where the Python compiler
  spends its time.
- The runtime backend ships 644 KB fewer class bytes (no definition or introspection classes, no 120 service
  entries) and 144 KB of models and catalog instead. The build-time model backend's classes are 40% smaller than
  the compiler's (252 KB against 368 KB of definitions, 124 KB against 276 KB of introspections: they initialize
  from the model instead of carrying the metadata as bytecode) but its output also carries the models.
- Context start (4.4 s, dominated by creating the GraalPy context and its first import) and first bean (3.3 s to
  3.7 s, the Python side of the first bean) do not move. The runtime backend's context start is inside the spread
  of the other two.
- The first introspection is faster in the runtime backend (9 ms against 23 ms and 29 ms): materializing one
  model and defining one class is cheaper than initializing the compiler's introspection class. Resolving all 40
  introspections is slower (64 ms against 29 ms and 21 ms): about 1.6 ms of generation per class against class
  loading. Neither is visible next to the Python costs.
- Retained heap after GC differs by about 1 MB (42.5 MB against 41.4 MB) with 120 generated classes, and the
  loaded-class count by about 85 classes (the runtime module and ASM).

No startup or memory benefit is claimed from these runs; the benefits established are the smaller packaged
output and the removal of the metadata classes from the build, at no measurable cost. The plan's other
performance workstreams (Python transformation, wrapper reduction, target-type mapping consolidation) are where
the compilation time is.

# Python metadata backends: measurements

- java: OpenJDK 64-Bit Server VM 25.0.4.1+1-LTS
- os: Linux amd64
- cpus: 4
- commit: f3aecece839c4717d4b00da4a47c7c7e3e14f702
- graalpy: 25.3.4.1
- pythonRuntime: GraalPy fallback interpreter (no Truffle JIT on this JDK: the Graal compiler is not on the class path)
- micronaut: 5.2.3-SNAPSHOT
- fixture: [modules:40, beans:80, introspections:40]

## Compilation (ms, 3 alternating rounds per backend)

| backend | median | min | max |
|---|---:|---:|---:|
| compiler | 5332.4 | 5111.4 | 22684.7 |
| model-build-time | 7274.8 | 5001.4 | 11591.4 |
| model-runtime | 5230.9 | 5147.3 | 8069.9 |

## Output inventory

| backend | kind | files | bytes |
|---|---|---:|---:|
| compiler | definitions | 80 | 367848 |
| compiler | introspections | 40 | 276373 |
| compiler | pythonResources | 84 | 392264 |
| compiler | serviceEntries | 120 | 0 |
| compiler | targetTypeMappings | 600 | 685941 |
| compiler | wrapperClasses | 121 | 540900 |
| compiler | wrapperSources | 120 | 634656 |
| model-build-time | definitions | 80 | 252390 |
| model-build-time | introspections | 40 | 123533 |
| model-build-time | models | 120 | 134891 |
| model-build-time | pythonResources | 84 | 392264 |
| model-build-time | serviceEntries | 120 | 0 |
| model-build-time | targetTypeMappings | 600 | 685941 |
| model-build-time | wrapperClasses | 121 | 540900 |
| model-build-time | wrapperSources | 120 | 634656 |
| model-runtime | catalog | 1 | 9450 |
| model-runtime | models | 120 | 134891 |
| model-runtime | pythonResources | 84 | 392264 |
| model-runtime | targetTypeMappings | 600 | 685941 |
| model-runtime | wrapperClasses | 121 | 540900 |
| model-runtime | wrapperSources | 120 | 634656 |

## Startup and first use in a fresh JVM (ms, medians; fallback interpreter)

### Scenario: few

| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| compiler | 4440.7 | 3606.3 | 0.0 | 24.5 | 0.9 | 1523.3 | 0.0 | 21870.0 | 41.0 | 12492.0 |
| model-build-time | 4512.6 | 3735.2 | 0.0 | 29.0 | 1.1 | 1914.5 | 0.0 | 21919.0 | 42.1 | 12353.0 |
| model-runtime | 4442.6 | 3559.9 | 0.0 | 9.0 | 0.5 | 1621.9 | 81.0 | 21915.0 | 42.1 | 12343.0 |

### Scenario: many

| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| compiler | 4357.1 | 3325.1 | 61.6 | 23.1 | 29.1 | 1556.1 | 0.0 | 21868.0 | 41.4 | 11886.0 |
| model-build-time | 4474.3 | 3527.4 | 61.5 | 29.0 | 20.6 | 1509.4 | 0.0 | 21917.0 | 42.3 | 12436.0 |
| model-runtime | 4453.6 | 3638.3 | 62.9 | 8.5 | 64.0 | 1479.1 | 120.0 | 21953.0 | 42.5 | 12078.0 |

