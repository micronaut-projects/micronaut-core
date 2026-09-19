# Java imports without generated Python shims

Branch `claude/kind-euler-2nyv6v`, stacked on the head of #13249 (`python/lazy-shim-subpackages`).

## Summary

- **What changed.** The Python compiler no longer generates a Python module tree for imported Java
  packages, classes and annotations (an initialiser, a members module and a decorator module per
  package and annotation). It writes one manifest per compilation
  (`__micronaut_java_imports_<hash>.py`, three dict literals), and a meta path finder in the new
  `micronaut_java_imports` module serves those packages as file-less modules whose members resolve
  to host classes and annotation decorators on first access.
- **Why.** The generated tree only made `from micronaut.context.annotation import Executable`
  importable. Walking it nested a package initialiser per level, and GraalPy's Java frames per Python
  frame made that depth the startup stack risk that #13229, #13242 and #13249 each mitigated.
  Removing the tree removes the cause.
- **Result.** Generated modules imported at startup 73 -> 1; deepest import chain 55 -> 23 Python
  frames; the launcher and `main.py` phase 390-610 ms -> 40-85 ms per warm context; a warm context
  1.56 s -> 1.24 s and the first context 7.8 s -> 7.2 s on the `test-suite-python` class path.
- **Verification.** `:micronaut-inject-python:test` (127), `:micronaut-inject-python-test:test`
  (907), `:micronaut-context-python:test` (198, 2 skipped), `:test-suite-python:test` (224, the
  opt-in benchmark skipped) and checkstyle pass.
- **Behaviour changes.** A Python package on the path wins deterministically over a Java `io.<name>`
  package of the same name (documented in `keywords.adoc`); an application module named like a Java
  type imported from its package is a specific compile error (previously an incidental "written
  twice"); a Java package module has no `__file__` and usually an empty `__path__`; a JDK sub-package
  the sources never import (`java.util.concurrent`) falls through to GraalPy's own finder.

## Design

- `micronaut_java_imports.py` (context-python, re-exported by `micronaut_runtime`): on the first
  Java import it lists every `sys.path` entry for manifests, runs each like a members module (so the
  compiler's bytecode cache applies) and merges them. `_MicronautJavaImportFinder` sits just before
  `PathFinder`; `_MicronautJavaPackage` resolves members on first access and lists members then
  direct sub-packages in `__all__`; `_MicronautJavaAnnotation` is one cached callable per annotation
  type carrying `java_class_name`/`java_class`, nested types as attributes and the bare-application
  guard; `_MicronautJavaType` is the facade for a class missing at run time. An application package
  with an `__init__.py` wins over the finder and its generated initialiser falls back to the Java
  members. A directory without an initialiser becomes part of the Java package's `__path__`.
- `PythonAnnotationProcessor`: `writeAllToVFS` is replaced by `writeJavaImportsManifest`; the shim
  prelude and class-binding helpers are gone; the initialiser keeps only the members-merging part
  plus the Java fallback. `GraalPyContextFactory.buildContext` installs the finder before the
  launcher and `main.py` run.
- Tests: `LazyShimSubpackageSpec` became `JavaImportFinderSpec` (run-time probes of every import
  form, decorators, type modules, missing names); file-layout assertions became manifest assertions
  through `JavaImportsManifest`.

## Benchmark

`PythonBootstrapBenchmark` (enabled by `MICRONAUT_PYTHON_BENCHMARK=true`, results to the file named
by `MICRONAUT_PYTHON_BENCHMARK_OUTPUT`) builds six contexts on one engine from the
`test-suite-python` class path. Per context it records `GraalPyContextFactory.buildContext` plus the
import of `micronaut_runtime` (charged to both revisions), the generated modules in `sys.modules`,
the file-less Java package modules, the deepest Python frame chain an import is reached from, and
the manifest read time. Fresh JVMs, alternating revisions, two rounds each. Temurin 25, Linux x64,
Truffle fallback interpreter (no JIT for Python).

### Headline

|                                                   | PR head (#13249)              | This branch                          |
|---------------------------------------------------|-------------------------------|--------------------------------------|
| Generated modules imported at startup             | 73                            | 1                                    |
| of which Java shim modules                        | 72                            | 0                                    |
| Java packages served as file-less modules         | 0                             | 13                                   |
| Modules in `sys.modules` after bootstrap          | 119                           | 164                                  |
| Deepest import chain (Python frames)              | 55 (`org.apiguardian.api.API`) | 23 (`micronaut.test`)               |
| Launcher and `main.py`, warm contexts             | 390-610 ms                    | 40-85 ms (+ 37-78 ms finder install) |
| Launcher and `main.py`, first context             | 1202 ms                       | 206 ms (+ 312 ms finder install)     |
| First context in the JVM, mean of rounds          | 7.79 s                        | 7.18 s                               |
| Warm contexts (runs 2-6), mean of rounds          | 1.56 s                        | 1.24 s                               |
| Manifest read and merge per context               | -                             | 13-30 ms (22-24 ms first)            |
| `PythonBootstrapStackTest` bound                  | 100 frames                    | 40 frames                            |

### Launcher and `main.py` per context (round 2, ms)

|                                                       | run 1 | run 2 | run 3 | run 4 | run 5 | run 6 | warm mean |
|-------------------------------------------------------|------:|------:|------:|------:|------:|------:|----------:|
| PR head, launcher and `main.py`                       |  1202 |   606 |   539 |   465 |   394 |   394 |       480 |
| This branch, finder install (module + manifest read)  |   312 |    78 |    47 |    56 |    43 |    37 |        52 |
| This branch, launcher and `main.py`                   |   206 |    83 |    51 |    53 |    42 |    37 |        53 |
| This branch, both phases                              |   518 |   161 |    98 |   109 |    85 |    74 |       105 |
| Manifest read and merge, round 1                      |  23.7 |  16.7 |  16.0 |  14.9 |  18.5 |  13.3 |      15.9 |
| Manifest read and merge, round 2                      |  22.4 |  16.7 |  29.7 |  14.4 |  14.2 |  15.3 |      18.1 |

### Context build time per run (s, including the `micronaut_runtime` import)

|                       | run 1 | run 2 | run 3 | run 4 | run 5 | run 6 | warm mean |
|-----------------------|------:|------:|------:|------:|------:|------:|----------:|
| PR head, round 1      |  7.76 |  2.22 |  1.46 |  1.40 |  1.41 |  1.21 |      1.54 |
| PR head, round 2      |  7.81 |  1.92 |  1.65 |  1.48 |  1.43 |  1.45 |      1.59 |
| This branch, round 1  |  7.08 |  1.71 |  1.27 |  1.29 |  1.17 |  1.03 |      1.29 |
| This branch, round 2  |  7.27 |  1.48 |  1.40 |  1.09 |  1.13 |  0.87 |      1.20 |

### Where the remaining time goes

A warm context is about 1.2 s, of which 80-160 ms is the launcher, the application modules and the
finder. The rest is GraalPy: creating the context and importing `micronaut_runtime` with asyncio,
roughly one second in the interpreter. A first prototype installed the finder by importing
`micronaut_runtime` during bootstrap, which showed as "Java import finder installed in 950-1500 ms"
per warm context; splitting the finder into `micronaut_java_imports` brought that phase to 37-78 ms
and left the runtime import deferred, as on the PR head. `micronaut_runtime` is not in the VFS file
list of `micronaut-context-python` (only `micronaut_asyncio` is), so every context compiles it from
the class path resource through the fallback finder; that is the largest remaining bootstrap cost
and unrelated to this change. The `-Xss` threshold experiment from #13249 was not repeated.

## Follow-ups

- The compile-time transformer still renders full decorator source for every imported annotation,
  though only the annotation name and kind reach the manifest.
- `micronaut_runtime` could be listed in the VFS file list and bytecode-compiled like
  `micronaut_asyncio`.
