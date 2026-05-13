---
name: micronaut-sourcegen
description: Add, integrate, or review Micronaut Sourcegen usage in modules that generate Java source, Kotlin source, Groovy-compatible source, or bytecode from ObjectDef, TypeDef, MethodDef, ExpressionDef, StatementDef, and SourceGenerator APIs.
license: Apache-2.0
compatibility: Micronaut framework and library modules that use io.micronaut.sourcegen for compile-time generation
metadata:
  author: Denis Stepanov
  version: "1.0.0"
---

# Micronaut Sourcegen

Use this skill when a Micronaut module wants to generate types with Micronaut Sourcegen instead of handwritten source strings, reflection, JavaPoet/KotlinPoet calls in visitors, or ad hoc bytecode. The target module may emit Java source, Kotlin source, Groovy-compatible source, or bytecode, but the generation logic should model the type once with `io.micronaut.sourcegen.model`.

## Goal

Help other modules adopt Micronaut Sourcegen safely: choose the right backend, wire the right processor dependencies, build language-neutral model definitions, and verify generated Java/Kotlin/bytecode behavior with targeted tests.

## Trigger Examples

Should trigger:

- "Use Micronaut Sourcegen in this module to generate a repository implementation."
- "Generate bytecode instead of Java source for this visitor."
- "Make this annotation processor generate Java and Kotlin outputs with Sourcegen."
- "Replace handwritten generated-source strings with `ClassDef` and `MethodDef`."
- "Add method bodies with switches, static calls, static field arguments, and constructor calls."

Should not trigger:

- "Only edit user guide text."
- "Only change Gradle publishing metadata."
- "Only refactor backend writer internals without a module consuming Sourcegen."
- "Explain Micronaut annotation processing without generated types."

## Procedure

1. Identify the consuming module and generation target.
2. Choose one backend for each compilation path.
3. Wire dependencies using the repository version catalogs and processor configurations.
4. Implement generation in a visitor with Sourcegen model builders and helpers.
5. Emit through `SourceGenerator.write(...)` or bytecode writer APIs as appropriate.
6. Add cross-target tests for the generated contract.
7. Run focused compile/test/style verification.

### 1) Identify the Consuming Module

- Confirm whether the module is an annotation processor/generator module, an application/test-suite module consuming a generator, or a backend writer module.
- Locate existing visitor registration under `META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor`.
- Identify source language paths:
  - Java annotation processing uses `annotationProcessor`.
  - Kotlin compilation usually uses `ksp`.
  - Bytecode generation runs during Java-language processing and writes class files directly.
- Inspect existing generated-type tests before changing behavior.

### 2) Choose the Backend

- Use `sourcegen-generator-java` when the module should emit `.java` source for `VisitorContext.Language.JAVA`; it also provides `GroovyPoetSourceGenerator` for `GROOVY`.
- Use `sourcegen-generator-kotlin` when Kotlin/KSP processing should emit `.kt` source for `VisitorContext.Language.KOTLIN`.
- Use `sourcegen-generator-bytecode` when Java-language processing should emit `.class` files directly through `ByteCodeGenerator`.
- Do not put both Java-source and bytecode Sourcegen generators on the same Java annotation-processor path unless the module intentionally owns selection. Both advertise `VisitorContext.Language.JAVA`, and `SourceGenerators.findByLanguage(JAVA)` expects a single effective backend.
- Use `sourcegen-bytecode-writer` directly only when implementing or testing bytecode writer behavior, not as the normal integration point for consuming annotation processors.

### 3) Wire Dependencies

- In a generator/processor module, depend on `sourcegen-generator` for the model and service lookup APIs. Add `sourcegen-annotations` only when the module consumes Sourcegen annotations such as builders/wither support.
- In a Java consumer/test module, add the chosen backend and the custom generator to `annotationProcessor`.
- In a Kotlin consumer/test module, add the Kotlin backend and KSP-compatible custom generator to `ksp`.
- In a bytecode consumer/test module, add `sourcegen-generator-bytecode` to `annotationProcessor`.
- Use Gradle version catalogs and existing project accessors. Do not hardcode dependency coordinates or versions.

Example repository-local shapes:

```kotlin
dependencies {
    implementation(projects.sourcegenGenerator)

    annotationProcessor(projects.sourcegenGeneratorJava)
    annotationProcessor(projects.myCustomGenerators)
}
```

```kotlin
dependencies {
    ksp(projects.sourcegenGeneratorKotlin)
    ksp(projects.myCustomGeneratorsKotlin)
}
```

```kotlin
dependencies {
    annotationProcessor(projects.sourcegenGeneratorBytecode)
    annotationProcessor(projects.myCustomGenerators)
}
```

### 4) Build Models With Factories, Not Record Constructors

- Always create Sourcegen model elements through builders, factory methods, or helper methods: `ClassDef.builder`, `InterfaceDef.builder`, `RecordDef.builder`, `EnumDef.builder`, `AnnotationObjectDef.builder`, `MethodDef.builder`, `MethodDef.constructor`, `FieldDef.builder`, `PropertyDef.builder`, `ParameterDef.builder`, `TypeDef.of`, `ClassTypeDef.of`, `TypeDef.parameterized`, `ExpressionDef.constant`, `StatementDef.multi`, `StatementDef.doTry`, `expr.returning`, `expr.invoke`, `type.invokeStatic`, `type.getStaticField`, and switch/if helper methods.
- Do not instantiate DSL records directly in consuming modules, for example avoid `new ClassTypeDef.Parameterized(...)`, `new TypeDef.Array(...)`, `new ExpressionDef.Constant(...)`, `new StatementDef.Switch(...)`, or other `new ...record(...)` forms.
- If a construct has no public helper yet, add or use a small named helper/factory at the nearest appropriate level instead of spreading direct record construction through module code.
- Keep model construction language-neutral. Do not call JavaPoet, KotlinPoet, ASM, or write source strings from a visitor unless the task is explicitly inside a backend writer.

### 5) Implement Visitors

- Prefer `TypeElementVisitor` with `VisitorKind.ISOLATING` when generated output depends only on the visited element.
- Resolve the backend through `SourceGenerators.findByLanguage(context.getLanguage()).orElse(null)` and return when none is available.
- Build `ObjectDef` instances from Micronaut AST metadata (`ClassElement`, `MethodElement`, `FieldElement`, `PropertyElement`) when possible.
- Emit with `sourceGenerator.write(objectDef, context, originatingElement)`.
- Use `SourceGenerators.handleFatalException(...)` when generation may postpone to a later round.

Minimal visitor shape:

```java
SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
if (sourceGenerator == null) {
    return;
}

ClassDef generated = ClassDef.builder(element.getPackageName() + ".Generated")
    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
    .addMethod(MethodDef.builder("name")
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeDef.STRING)
        .build((aThis, params) -> ExpressionDef.constant("generated").returning()))
    .build();

sourceGenerator.write(generated, context, element);
```

### 6) Model Generated Behavior

- Use `ClassDef`, `InterfaceDef`, `RecordDef`, `EnumDef`, and `AnnotationObjectDef` for generated type kind.
- Use `TypeDef` and `ClassTypeDef` for primitives, arrays, generics, generated class names, nullability, and static field owners.
- Use `MethodDef` body builders for executable logic. Prefer explicit `returns(...)` for public or non-trivial generated methods.
- When generated code reads or writes a field that the same generator defines, keep the `FieldDef` instance and reference it with `aThis.field(fieldDef)` or `generatedType.getStaticField(fieldDef)` instead of repeating the field name and type.
- When generated code invokes a method that the same generator defines, keep the `MethodDef` instance and invoke it with `receiver.invoke(methodDef, ...)` or `generatedType.invokeStatic(methodDef, ...)` instead of repeating the method name, parameter types, and return type.
- When generated code targets an existing method or field that is accessible reflectively and present on the annotation processor classpath, prefer a reflective handle from `ReflectionUtils.getRequiredMethod(...)` or `ReflectionUtils.getRequiredField(...)`. Pass method handles to `receiver.invoke(method, ...)` or `type.invokeStatic(method, ...)`; pass static field handles to `type.getStaticField(field)`; for instance fields, centralize the field-name/type bridge in a helper fed by the reflective `Field`. This uses reflection only at generation time to describe a known member; it must not generate runtime reflection.
- Use `StatementDef.multi(...)`, `expr.returning()`, `expr.newLocal(...)`, `condition.doIf(...)`, `expr.asExpressionSwitch(...)`, `expr.asStatementSwitch(...)`, `StatementDef.doTry(...)`, and invocation helpers to compose bodies.
- Use `ClassTypeDef.getStaticField("NAME", valueType)` for enum constants, static constants, class literals, and static-field arguments passed to invocations.
- String switches are supported. For string switch cases, use a `TypeDef.STRING` switch expression and `ExpressionDef.constant("case")` keys with `asExpressionSwitch(...)` or `asStatementSwitch(...)`.

### 7) Verify

- Compile the generator module and the consuming test module.
- Run targeted tests that load or compile generated types for each backend touched.
- For Java source generation, verify generated `.java` behavior through Java test-suite tests.
- For Kotlin source generation, verify KSP and Kotlin tests.
- For bytecode generation, verify runtime behavior and bytecode writer tests when the model shape exercises bytecode-specific paths.
- Finish source changes with the repository checks required by `AGENTS.md`: affected compile tasks, targeted tests, full affected module tests, `./gradlew -q cM`, and `./gradlew -q spotlessCheck` if new files were added.

## Backend Notes

- Java source backend: `JavaPoetSourceGenerator` writes Java source for `VisitorContext.Language.JAVA`.
- Groovy-compatible source backend: `GroovyPoetSourceGenerator` reuses the Java source generator for `GROOVY`.
- Kotlin source backend: `KotlinPoetSourceGenerator` writes Kotlin source for `VisitorContext.Language.KOTLIN`.
- Bytecode backend: `ByteCodeGenerator` advertises `JAVA` and writes class files through `context.visitClass(...)`; it does not support `write(ObjectDef, Writer)`.

## Cookbook

Use `references/sourcegen-cookbook.md` for adoption patterns covering:

- Gradle dependency placement for generator modules and consumers,
- backend selection for Java, Kotlin, Groovy-compatible source, and bytecode,
- type/model construction using builders and helper methods,
- generated classes, interfaces, records, enums, annotations, method bodies, switches, invocations, static fields, arrays, lambdas, and super constructors,
- testing and validation across source and bytecode backends.

## Guardrails

- Do not instantiate Sourcegen DSL records directly in consuming modules; use builders, factories, or named helper methods.
- Do not duplicate generated member metadata in generated code bodies; prefer the existing `FieldDef` or `MethodDef` instance when reading/writing generated fields or invoking generated methods.
- Do not use string-based references for known existing classpath methods/static fields when a `ReflectionUtils.getRequiredMethod(...)` or `ReflectionUtils.getRequiredField(...)` handle can describe the member safely.
- Do not generate source text with string concatenation when a Sourcegen model construct exists.
- Do not place multiple `JAVA` Sourcegen backends on the same annotation-processor path unless selection is explicit and tested.
- Do not use backend-specific JavaPoet/KotlinPoet/ASM APIs from normal visitors.
- Do not use reflection for generation metadata when Micronaut AST elements are available.
- Do not break public Sourcegen APIs without explicit approval and compatibility checks.

## Validation Checklist

- [ ] Skill folder name and `name:` frontmatter are `micronaut-sourcegen`.
- [ ] Consuming module and target backend are identified.
- [ ] Java/Kotlin/bytecode processor dependencies are scoped correctly.
- [ ] Only one effective `JAVA` Sourcegen backend is present for a Java compilation path.
- [ ] Model definitions use builders/factories/helpers, not direct DSL record constructors.
- [ ] Generated fields/methods referenced from generated bodies are referenced through their `FieldDef`/`MethodDef` instances where available.
- [ ] Generated sources/classes are emitted through `SourceGenerator.write(...)` or backend writer APIs where appropriate.
- [ ] Targeted and full affected tests pass, or unrun checks are reported clearly.

## References

- `references/sourcegen-cookbook.md`
