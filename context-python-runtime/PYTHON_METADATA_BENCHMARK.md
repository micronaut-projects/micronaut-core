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

Commit `ab2d944ac8753e9328775b7048e2973182a70561` (this branch), Sourcegen 2.1.0, ASM 9.10.1, Gradle 9.7.1, the
Micronaut version below is the one the built runtime reports.

Measured on Oracle GraalVM 25.0.4, and still without the Truffle JIT. Each measured JVM reports the Truffle runtime
it resolved, and every run of this set reports `Interpreted`. GraalPy gives the reason itself: `The polyglot engine
uses a fallback runtime that does not support runtime compilation to native code. The following cause was found:
Version check failed.` The Python runtime of this repository is GraalPy 25.3.4.1, whose Truffle requires a Graal
compiler of its own version, and the newest GraalVM published for JDK 25 carries the 25.0 compiler. A JIT run needs
a 25.3-aligned GraalVM. The numbers below therefore measure the same interpreted Python runtime as the earlier
Temurin set, on a different JDK; the Python costs they are dominated by would be much smaller with a JIT, which
would make the differences between the backends more visible, not less.

## Reading the numbers

- Compilation time is the same for the three backends within the noise of this machine: the compiler and runtime
  medians are 6.0 s and 5.8 s, and every backend's own spread (5.1 s to 23.1 s) dwarfs the difference between
  them, first compilations of a session included. Emitting the metadata classes is not where the Python compiler
  spends its time.
- The runtime backend ships 644 KB fewer class bytes (no definition or introspection classes, no 120 service
  entries) and 144 KB of models and catalog instead. The build-time model backend's classes are 40% smaller than
  the compiler's (252 KB against 368 KB of definitions, 124 KB against 276 KB of introspections: they initialize
  from the model instead of carrying the metadata as bytecode) but its output also carries the models.
- Context start (4.5 s to 4.7 s, dominated by creating the GraalPy context and its first import) and first bean
  (4.3 s to 4.6 s, the Python side of the first bean) do not move. The runtime backend's context start is inside
  the spread of the other two.
- The first introspection is faster in the runtime backend (9.6 ms against 16.8 ms and 27.4 ms): materializing one
  model and defining one class is cheaper than initializing the compiler's introspection class. Resolving all 40
  introspections is slower (65 ms against 34 ms and 28 ms): about 1.6 ms of generation per class against class
  loading. Neither is visible next to the Python costs.
- Retained heap after GC differs by about 1 MB (42.9 MB against 41.6 MB) with 120 generated classes, and the
  loaded-class count by about 85 classes (the runtime module and ASM).

## Comparisons

Two comparisons are available from the recorded sets in git history: the same implementation on two JDKs, and the
finished implementation against the first one on the same JDK. Both are three rounds on a four-CPU machine, so
anything under about 15% is noise; the compiler backend, which this work does not touch, is the control.

**Oracle GraalVM 25.0.4 against Temurin 25.0.4.1, same commit.** Truffle is interpreted on both, so this compares
two HotSpots rather than two Python runtimes. GraalVM is slower for this workload across every backend: context
start +2% to +5%, first bean +22% to +35%, all beans +22% to +32%, warm restart +50% to +64%, JVM wall +24% to
+31%, with about 160 more loaded classes and a few tenths of a megabyte more heap. Its JIT compiles the Java code
with libgraal, and a JVM that lives fifteen seconds pays that warmup without earning it back. The ordering and the
size of the gaps between the backends are the same on both JDKs, so the backend comparison does not depend on the
JVM it was taken on.

**The finished implementation against the batch 1-4 one (`604b891a`), both on Temurin.** Two effects are the
implementation, both small and both expected of a model that now carries factories, configuration binding, iterable
beans, enums, described members and validation flags: the saved model grew 2.6% (3.6 KB over 40 modules, 131 KB to
135 KB) and the generated definition classes 1.6% (248 KB to 252 KB), while the generated introspections are
unchanged. The first introspection of the runtime backend went from 6.0 ms to 8.5 ms, decoding a richer model.
Everything else (context start +11% to +15%, first bean +10% to +22%, JVM wall about +10%) moved by the same amount
in the control, so it is the machine rather than the code. The output inventory is otherwise unchanged: the runtime
backend still emits no definition and no introspection class.

No startup or memory benefit is claimed from these runs; the benefits established are the smaller packaged
output and the removal of the metadata classes from the build, at no measurable cost. The plan's other
performance workstreams (Python transformation, wrapper reduction, target-type mapping consolidation) are where
the compilation time is.

# Python metadata backends: measurements

- java: Java HotSpot(TM) 64-Bit Server VM 25.0.4+7-LTS-jvmci-b01
- os: Linux amd64
- cpus: 4
- commit: ab2d944ac8753e9328775b7048e2973182a70561
- graalpy: 25.3.4.1
- micronaut: 5.2.3-SNAPSHOT
- fixture: [modules:40, beans:80, introspections:40]

- pythonRuntime: GraalPy on the Truffle runtime the measured JVMs resolved: Interpreted

## Compilation (ms, 3 alternating rounds per backend)

| backend | median | min | max |
|---|---:|---:|---:|
| compiler | 5952.4 | 5711.7 | 23100.1 |
| model-build-time | 6260.3 | 5077.1 | 9095.5 |
| model-runtime | 5795.1 | 5537.7 | 7325.2 |

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
| model-build-time | definitions | 80 | 252390 |
| model-build-time | introspections | 40 | 123533 |
| model-build-time | models | 120 | 134891 |
| model-build-time | pythonResources | 84 | 392264 |
| model-build-time | serviceEntries | 120 | 0 |
| model-build-time | targetTypeMappings | 600 | 685941 |
| model-build-time | wrapperClasses | 121 | 540901 |
| model-build-time | wrapperSources | 120 | 634656 |
| model-runtime | catalog | 1 | 9450 |
| model-runtime | models | 120 | 134891 |
| model-runtime | pythonResources | 84 | 392264 |
| model-runtime | targetTypeMappings | 600 | 685941 |
| model-runtime | wrapperClasses | 121 | 540901 |
| model-runtime | wrapperSources | 120 | 634656 |

## Startup and first use in a fresh JVM (ms, medians; Truffle runtime: Interpreted)

### Scenario: few

| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| compiler | 4782.6 | 4634.0 | 0.1 | 20.1 | 1.6 | 2336.0 | 0.0 | 22030.0 | 41.4 | 15593.0 |
| model-build-time | 4488.0 | 4383.6 | 0.0 | 27.4 | 1.0 | 2870.6 | 0.0 | 22080.0 | 42.9 | 15667.0 |
| model-runtime | 4627.6 | 4373.8 | 0.0 | 9.5 | 0.5 | 2588.3 | 81.0 | 22074.0 | 42.7 | 15530.0 |

### Scenario: many

| backend | context start | first bean | all beans | first introspection | all introspections | warm start | generated classes | loaded classes | heap after GC (MB) | JVM wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| compiler | 4531.2 | 4488.7 | 79.7 | 16.8 | 33.7 | 2337.6 | 0.0 | 22032.0 | 41.6 | 15551.0 |
| model-build-time | 4581.3 | 4307.0 | 81.2 | 27.4 | 28.4 | 2474.6 | 0.0 | 22077.0 | 42.4 | 15383.0 |
| model-runtime | 4665.8 | 4422.5 | 76.6 | 9.6 | 65.5 | 2311.5 | 120.0 | 22115.0 | 42.9 | 15430.0 |

