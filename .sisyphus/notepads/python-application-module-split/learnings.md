## Inherited Wisdom
- Target annotation move: `io.micronaut.context.python.annotation.PythonApplication`
- Processor must detect annotation via metadata (string FQCN, no class literals)
- Keep build/test commands agent-driven per plan
- [2026-03-10T18:23:00Z] Task 1 done: annotation moved to `context-python` (now `io.micronaut.context.python.annotation.PythonApplication`) and old source removed; module compiles (`:micronaut-context-python:compileJava`).
## Findings
- Moved @PythonApplication into :micronaut-context-python under io.micronaut.context.python.annotation and added package-level nullability (NullMarked) for the new annotation package.
- Removed io.micronaut.python.processing.annotation.PythonApplication and its package-info; note that other classes still reside in io.micronaut.python.processing.annotation (PythonAnnotationMetadataBuilder, PythonElementAnnotationMetadataFactory).
- Gradle module name for context-python is :micronaut-context-python (not :context-python).
- [2026-03-10T18:43:00Z] Task 2 done: `PythonAnnotationProcessor` now resolves the annotation via `Elements#getTypeElement("io.micronaut.context.python.annotation.PythonApplication")`, uses `roundEnv.getElementsAnnotatedWith(TypeElement)`, and reads `code`/`src` via `AnnotationMirror` metadata, with no direct class literal or import remaining.

- [2026-03-10T19:35:00Z] Task 3 verified: `PyronautCompiler.generateMainClassSource` emits `import io.micronaut.context.python.annotation.PythonApplication;` for generated main sources. Updated inject-python test stub (`GeneratedJavaSourceSpec`) to import the new FQCN as well. Verified via `rg` on `PyronautCompiler.java` and `./gradlew :micronaut-inject-python-test:test --tests 'io.micronaut.python.compiler.GenerateToStringEqualsSpec'` (PASS).

- [2026-03-10T22:20:00Z] Task 6 rerun: manual verification commands executed
  - `./gradlew -q :micronaut-context-python:compileTestJava :micronaut-inject-python:compileTestJava :micronaut-inject-python-test:compileTestGroovy` completed successfully.
  - `./gradlew :micronaut-inject-python-test:test --tests 'io.micronaut.python.compiler.PyronautCompilerSpec'` passed (14 tests, 1 skipped) with the usual Kotlin plugin and Graal warnings.
  - `./gradlew :micronaut-context-python:test :micronaut-inject-python:test :micronaut-inject-python-test:test` fails only because `io.micronaut.python.annotation.processing.test.AroundAdviceSpec` cannot create a ByteBuddy proxy for primitive `int` (existing issue triggered by `PythonProxyCreator`).
  - `./gradlew -q cM`, `./gradlew -q spotlessCheck`, and `./gradlew -q spotlessApply` still fail in `:micronaut-context-python:spotlessJavaMisc` because the package-info files for the `aop`, root, and `scope` packages do not match the expected `/**` delimiter for the license header.
  - `rg -n "io\.micronaut\.python\.processing\.annotation\.PythonApplication"` only hits the migration spec, and `rg -n "io\.micronaut\.context\.python\.annotation\.PythonApplication"` only hits the expected Pyronaut processor files.
