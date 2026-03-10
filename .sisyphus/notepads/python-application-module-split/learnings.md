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
