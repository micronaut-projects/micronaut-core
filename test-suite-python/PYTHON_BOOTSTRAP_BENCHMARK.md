# Java imports without generated Python shims

Branch `claude/kind-euler-2nyv6v`, stacked on the head of #13249 (`python/lazy-shim-subpackages`); pull request #13250.

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
- **Result.** Generated modules imported at startup 73 -> 3 (none of them a Java shim); deepest import
  chain 55 -> 23 Python frames; the launcher and `main.py` phase of a warm context
  571 ms -> 71 ms (plus 53 ms to install the finder) on Temurin 25 and
  764 ms -> 98 ms (plus 65 ms) on GraalVM 25; a warm context
  1.94 s -> 1.64 s on Temurin and 2.65 s -> 2.19 s on GraalVM.
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
  guard (its "value holds a class" decision is recorded by the compiler, since the annotation may be
  absent at run time); `_MicronautJavaType` is the facade for a class missing at run time. An
  application package with an `__init__.py` wins over the finder; its generated initialiser falls back
  to the Java members, lists them in `__all__` and `dir()`, and keeps a Java type on its name when the
  type's module is imported. A directory without an initialiser becomes part of the Java package's
  `__path__`. A package imported as a module without a class import (`import a.b as p`) is recorded
  too. A reload of the module replaces the finder it installed.
- `PythonAnnotationProcessor`: `writeAllToVFS` is replaced by `writeJavaImportsManifest`; the shim
  prelude and class-binding helpers are gone; the initialiser keeps only the members-merging part
  plus the Java fallback. `GraalPyContextFactory.buildContext` imports `micronaut_java_imports`
  before the launcher and `main.py` run; `micronaut_runtime`, whose import (asyncio, bridge helpers)
  is deferred to the first bridge call, is not touched by that.
- `micronaut_java_imports.py` and `micronaut_runtime.py` are listed in the VFS file list of
  `micronaut-context-python`, so both load from the bytecode cache like `micronaut_asyncio`
  instead of being compiled from the class path resource per context.
- Tests: `LazyShimSubpackageSpec` became `JavaImportFinderSpec` (run-time probes of every import
  form, decorators, type modules, package-only imports, missing names); file-layout assertions became
  manifest assertions through `JavaImportsManifest`; `CompilerSilentFailureSpec` probes the mixed
  package (star import of Java and Python members, type module keeping the class).

## Benchmark

`PythonBootstrapBenchmark` (enabled by `MICRONAUT_PYTHON_BENCHMARK=true`; `MICRONAUT_PYTHON_BENCHMARK_CONTEXTS`
contexts, results to the file named by `MICRONAUT_PYTHON_BENCHMARK_OUTPUT`) builds contexts on one engine
from the `test-suite-python` class path. Per context it records `GraalPyContextFactory.buildContext` plus
the import of `micronaut_runtime` (charged to both revisions), the generated modules in `sys.modules`,
the file-less Java package modules, the deepest Python frame chain an import is reached from, and the
bootstrap phases logged at debug level. Ten contexts per JVM, fresh JVMs alternating revisions, two
rounds each, on this container (Linux x64), once on Temurin 25 (Truffle fallback interpreter, no JIT
for Python) and once on Oracle GraalVM 25.0.4 (JIT). Absolute times depend on the container's load
and were higher in this session than in an earlier one; the relative effect is what to read.

### Temurin 25 (fallback interpreter)

| | PR head (#13249) | This branch |
|---|---:|---:|
| Generated modules imported at startup | 73 (72 Java shims + launcher) | 3 (launcher, `micronaut_java_imports`, `micronaut_runtime`) |
| Java packages served as file-less modules | 0 | 13 |
| Deepest import chain (Python frames) | 55 (`org.apiguardian.api.API`) | 23 (`micronaut.test`) |
| Launcher and `main.py`, warm contexts | 416–871 ms (mean 571) | 43–175 ms (mean 71) + finder install 33–128 ms (mean 53) |
| Launcher and `main.py`, first context | 1978 / 1736 ms | 298 / 296 ms + finder install 301 / 289 ms |
| First context in the JVM, mean of rounds | 11.34 s | 10.19 s |
| Warm contexts (runs 2–10), mean of rounds | 1.94 s | 1.64 s (-15%) |

Launcher and `main.py` per context, and the finder install (module import and manifest read) on this branch:

| ms | run 1 | run 2 | run 3 | run 4 | run 5 | run 6 | run 7 | run 8 | run 9 | run 10 | warm mean (2–10) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| PR head, launcher and main.py, round 1 | 1978 | 781 | 615 | 693 | 489 | 416 | 480 | 536 | 464 | 468 | 549 |
| PR head, launcher and main.py, round 2 | 1736 | 871 | 785 | 646 | 536 | 493 | 483 | 473 | 556 | 496 | 593 |
| This branch, finder install, round 1 | 301 | 128 | 51 | 46 | 49 | 103 | 38 | 39 | 33 | 35 | 58 |
| This branch, launcher and main.py, round 1 | 298 | 175 | 75 | 62 | 61 | 95 | 50 | 116 | 43 | 47 | 80 |
| This branch, finder install, round 2 | 289 | 75 | 52 | 46 | 50 | 69 | 36 | 35 | 34 | 34 | 48 |
| This branch, launcher and main.py, round 2 | 296 | 94 | 72 | 61 | 62 | 75 | 49 | 47 | 47 | 48 | 62 |

Context build time per run, seconds, including the `micronaut_runtime` import:

| | run 1 | run 2 | run 3 | run 4 | run 5 | run 6 | run 7 | run 8 | run 9 | run 10 | warm mean (2–10) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| PR head, round 1 | 11.62 | 2.96 | 2.22 | 1.98 | 1.68 | 1.56 | 1.69 | 1.82 | 1.74 | 1.53 | 1.91 |
| PR head, round 2 | 11.06 | 2.76 | 2.24 | 2.10 | 2.06 | 1.84 | 1.81 | 1.72 | 1.68 | 1.48 | 1.97 |
| This branch, round 1 | 10.39 | 2.52 | 2.13 | 1.67 | 1.74 | 1.75 | 1.28 | 1.32 | 1.34 | 1.38 | 1.68 |
| This branch, round 2 | 10.00 | 2.43 | 1.95 | 1.74 | 1.81 | 1.42 | 1.44 | 1.40 | 1.20 | 1.05 | 1.60 |

### Oracle GraalVM 25 (JIT)

| | PR head (#13249) | This branch |
|---|---:|---:|
| Generated modules imported at startup | 73 (72 Java shims + launcher) | 3 (launcher, `micronaut_java_imports`, `micronaut_runtime`) |
| Java packages served as file-less modules | 0 | 13 |
| Deepest import chain (Python frames) | 55 (`org.apiguardian.api.API`) | 23 (`micronaut.test`) |
| Launcher and `main.py`, warm contexts | 531–1193 ms (mean 764) | 59–196 ms (mean 98) + finder install 26–105 ms (mean 65) |
| Launcher and `main.py`, first context | 1987 / 1943 ms | 293 / 281 ms + finder install 369 / 306 ms |
| First context in the JVM, mean of rounds | 12.59 s | 11.23 s |
| Warm contexts (runs 2–10), mean of rounds | 2.65 s | 2.19 s (-17%) |

| ms | run 1 | run 2 | run 3 | run 4 | run 5 | run 6 | run 7 | run 8 | run 9 | run 10 | warm mean (2–10) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| PR head, launcher and main.py, round 1 | 1987 | 1121 | 961 | 734 | 706 | 878 | 655 | 630 | 644 | 598 | 770 |
| PR head, launcher and main.py, round 2 | 1943 | 1193 | 1102 | 694 | 657 | 656 | 666 | 759 | 531 | 571 | 759 |
| This branch, finder install, round 1 | 369 | 90 | 93 | 62 | 95 | 57 | 57 | 53 | 62 | 47 | 68 |
| This branch, launcher and main.py, round 1 | 293 | 196 | 113 | 92 | 192 | 79 | 101 | 59 | 80 | 62 | 108 |
| This branch, finder install, round 2 | 306 | 105 | 42 | 66 | 74 | 58 | 78 | 26 | 51 | 48 | 61 |
| This branch, launcher and main.py, round 2 | 281 | 133 | 108 | 91 | 119 | 76 | 71 | 63 | 63 | 60 | 87 |

| | run 1 | run 2 | run 3 | run 4 | run 5 | run 6 | run 7 | run 8 | run 9 | run 10 | warm mean (2–10) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| PR head, round 1 | 12.68 | 3.88 | 3.45 | 2.65 | 2.66 | 2.70 | 2.33 | 2.19 | 2.11 | 2.15 | 2.68 |
| PR head, round 2 | 12.49 | 3.99 | 3.35 | 2.35 | 2.67 | 2.43 | 2.40 | 2.35 | 1.97 | 2.08 | 2.62 |
| This branch, round 1 | 11.31 | 3.21 | 2.73 | 2.29 | 2.52 | 2.07 | 2.06 | 1.86 | 1.77 | 1.41 | 2.21 |
| This branch, round 2 | 11.14 | 3.20 | 2.45 | 2.52 | 2.31 | 2.23 | 1.98 | 1.86 | 1.73 | 1.29 | 2.17 |

### Reading the numbers

- The phase this change targets, the launcher and the application modules importing their Java
  packages, drops by an order of magnitude on both runtimes. The rest of a context is GraalPy: creating
  the context (about 1.1 s in the first context, 1-2 ms afterwards) and importing `micronaut_runtime`
  with asyncio, which both revisions pay and which the benchmark forces on both.
- GraalVM was slower than Temurin here for both revisions: ten short-lived contexts in one JVM spend
  their time compiling in the JIT before it pays off, and the container gives the compiler threads
  little room. The relative gain is the same (-15% warm on Temurin, -17% on GraalVM).
- The first context includes JVM, engine and class-loading warm-up (10-12 s in this session).
- The `-Xss` threshold experiment from #13249 was not repeated. With no initialiser nesting, the import
  depth is what `PythonBootstrapStackTest` bounds, now at 40 frames instead of 100 (measured 23 on this
  branch, 55 on the PR head).

## Follow-ups

- The compile-time transformer still renders full decorator source for every imported annotation,
  though only the annotation name, kind and class-valued flag reach the manifest.
