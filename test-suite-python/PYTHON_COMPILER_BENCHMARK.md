# Python compiler baseline and the property hook cleanup

Branch `claude/python-compiler-baseline-ef4sxf`, stacked on the head of #13250 (`claude/kind-euler-2nyv6v`).

## Summary

- **What changed.** (1) The compiler can profile a compilation
  (`PyronautCompiler.Builder.profileReportFile`, or `-Dmicronaut.python.compiler.profile=<file>` on a
  Gradle build): inclusive phase times, javac rounds, the Python model counts and an inventory of
  the output. (2) Generated classes declare `micronautValueCoercibleSetMember` /
  `micronautValueCoerciblePutMember` only when the bodies do more than the interface defaults.
  (3) An end-to-end test of a mixed Python/Java package and of a class-valued annotation that is
  absent at run time, whose fixes #13250 carries.
- **Why.** The work plan for reducing Python compilation time starts from a corrected baseline
  measured with reproducible tooling; the hook cleanup is the first, size-only step of it.
- **Result.** On the `test-suite-python` sources (426 Python inputs), the hook cleanup removes 364
  trivial overrides from the 786 generated Java sources: 40.4 KB of method declarations, 41.0 KB of
  generated Java in total (2,013,911 -> 1,972,903 bytes, -2.0%) and 37.0 KB of class files
  (8,973,417 -> 8,936,451 bytes, -0.4%), with the same 786 sources and 1,828 class files. Compile time
  is unchanged: 34.3 s median inside the compiler on both revisions, within the 32.5-35.1 s spread of
  either. This is the expected outcome of a code-size cleanup and is reported as such, not as a
  speedup.
- **Verification.** With `-Ppython-ci`: `:micronaut-inject-python:test` (130),
  `:micronaut-inject-python-test:test` (913), `:micronaut-context-python:test` (198, 2
  skipped), `:test-suite-python:test` (224, the opt-in benchmark skipped) and checkstyle pass;
  `:micronaut-context-python-netty:test` passes except two IPv6 tests that fail the same way on the
  base revision in the sandbox used here, which has no IPv6.

## Corrected baseline

The compilation of the `test-suite-python` test sources, profiled by the compiler itself
(`:test-suite-python:compileTestPython -Dmicronaut.python.compiler.profile=...`). Temurin 25.0.4.1 on
Linux x64, 4 CPUs, the Gradle worker at its default 2 GB heap, GraalPy 25.3.4.1 on the Truffle fallback
interpreter (no JIT for Python). Every compilation ran in a fresh worker JVM: Gradle started a new
process-isolated worker for every build (JVM uptime at compile start 0.5-0.6 s in all 22
compilations), so a reused, warmed-up worker was not observed and the compiler's own
`PythonProcessingSession` reuse is not exercised by the Gradle task. The workload, revision order
and JVM were the same for both revisions; revisions alternated between runs.

Phase times are inclusive medians over the 11 compilations of the baseline revision, with the
minimum and maximum. The Python phases run inside the javac task (the annotation processor does the
Python work in the first round).

| Phase | Median | Range |
|---|---:|---|
| Compilation (`compiler.compile`) | 34.3 s | 32.7-35.1 s |
| javac task, all rounds | 33.9 s | 32.3-34.7 s |
| GraalPy context creation | 2.7 s | 2.6-2.8 s |
| AST transform (`micronaut_transformer`, 426 sources) | 13.2 s | 12.5-13.8 s |
| Model (`micronaut_processor`) | 4.9 s | 4.6-5.1 s |
| Python sources and bytecode to the VFS | 1.5 s | 1.1-1.6 s |
| Aggregating visitors | 0.4 s | 0.4-0.5 s |
| Isolating visitors (Java stub generation) | 3.4 s | 3.0-3.5 s |
| Bean definitions | 2.0 s | 1.6-2.1 s |
| javac task minus the Python phases (parsing and compiling the generated sources) | 5.9 s | 5.7-6.2 s |

Counts, identical in every compilation of the baseline:

| | |
|---|---:|
| Python inputs | 426 |
| Python classes in the model | 456 |
| javac rounds | 5 |
| Compilation units analysed by javac | 787 |
| Decorator entries rendered by the transformer (one per imported annotation per source) | 3,325 |
| Unique annotations behind them | 137 |
| Decorator source rendered, characters | 4,328,240 |
| Generated Java sources | 786 (2,013,911 bytes) |
| Class files | 1,828 (8,973,417 bytes) |
| Python sources in the VFS | 675 |
| Python bytecode files | 357 |
| Other resources | 1,100 |

The transform and the model together take 18.1 s of the 34.3 s; the 3,325 decorator entries for
137 unique annotations and their 4.3 million characters of rendered source are the evidence for
step 3 of the plan. The generated-source rounds of javac cost about 5.9 s on top of the Python
phases.

## The hook cleanup, measured against that baseline

Baseline: the profile commit. Candidate: the hook cleanup commit on top of it.
Three fresh-worker rounds with a single-use Gradle daemon and two rounds of four builds each with a
persistent daemon, the revision order alternating between rounds; all 22 compilations ran in a fresh
worker (see above), so they are pooled per revision.

| | Baseline (n=11) | Candidate (n=11) |
|---|---:|---:|
| Compilation, median [min-max] | 34.3 s [32.7-35.1] | 34.3 s [32.5-34.9] |
| javac task | 33.9 s [32.3-34.7] | 33.9 s [32.1-34.5] |
| AST transform | 13.2 s [12.5-13.8] | 13.0 s [12.7-13.6] |
| Model | 4.9 s [4.6-5.1] | 4.8 s [4.6-5.0] |
| Isolating visitors | 3.4 s [3.0-3.5] | 3.2 s [3.1-3.4] |
| Bean definitions | 2.0 s [1.6-2.1] | 2.1 s [1.6-2.2] |
| javac task minus the Python phases | 5.9 s [5.7-6.2] | 6.0 s [5.5-6.3] |
| Hook overrides in the generated sources | 442, of which 364 `return false` | 78, none trivial |
| Generated Java sources | 786 (2,013,911 bytes) | 786 (1,972,903 bytes) |
| Class files | 1,828 (8,973,417 bytes) | 1,828 (8,936,451 bytes) |
| Gradle build wall time, single-use daemon | 51.8 s [51.2-58.8] (n=3) | 51.4 s [51.2-57.8] (n=3) |
| Gradle build wall time, persistent daemon, later builds | 39.3 s [38.3-40.2] (n=6) | 38.7 s [38.2-39.2] (n=6) |

Per-compilation times inside the compiler, in the order they ran (s):

| | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| Baseline | 34.8 | 34.3 | 34.2 | 32.7 | 33.9 | 33.4 | 34.9 | 33.9 | 35.0 | 35.1 | 35.0 |
| Candidate | 34.1 | 34.4 | 34.3 | 32.5 | 33.8 | 34.8 | 34.2 | 34.3 | 34.9 | 34.3 | 34.4 |

The differences between the revisions are inside the spread of either; the cleanup is a size
reduction of the generated code and nothing more is claimed for it.

The branch was later rebased onto a newer head of #13250 (which changed the manifest writing and the
runtime module, not the compiler phases). A shorter confirmation run on the rebased revisions, five
fresh-worker compilations each, gave the same picture: 36.0 s [34.5-36.7] for the baseline against
35.7 s [34.8-36.7] for the candidate, both a little slower than the run above on a busier machine,
with byte-identical inventories (2,013,911 -> 1,972,903 bytes of generated Java, 8,973,417 ->
8,936,451 bytes of class files, 442 -> 78 hook overrides).

## The regressions the plan starts from

The three regressions reproduced before this work (a star import of an application package sharing
a Java package's name bound no Java members; importing a Java type's module through such a package
replaced the class bound on it with the module; a class-valued argument of an annotation absent at
run time failed as a bare application) are fixed on #13250, which this branch is stacked on, with
tests in `JavaImportFinderSpec` and `CompilerSilentFailureSpec`. `MixedPackageJavaImportsSpec` here
adds the end-to-end case: an annotation compiled into a directory the run time class loader does
not see, applied with a class argument at module import, next to a mixed package whose star
import, `__all__` order, `dir()` and type-module import are checked at run time.

## Reproducing

    ./gradlew :test-suite-python:compileTestPython -Ppython-ci -Dmicronaut.python.compiler.profile=build/profile.txt

appends one block of `key=value` lines per compilation (attributes, `phase.<name>.ms`,
`counter.<name>`, `artifact.<kind>.count` and `.bytes`) to `build/profile.txt`. Delete
`test-suite-python/build/classes/python/test` and pass `--no-build-cache` to compile again. The
`jvm.uptime.ms` attribute tells a fresh worker from a reused one. `PyronautCompiler.Builder`
offers the same through `profileReportFile(File)` and `profileCallback(Consumer)`.

## Follow-ups (the plan's next steps)

- Step 3a: describe each imported annotation once per compilation instead of rendering 3,325
  decorator bodies (4.3 million characters) for 137 unique annotations.
- Step 3b: hand the transformed AST to the model visitor instead of unparsing and re-parsing it.
- Step 4: consolidate the 312 target-type adapter sources and bean definitions.
