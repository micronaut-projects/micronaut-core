# Micronaut Sourcegen Cookbook

This reference is for Micronaut modules that want to generate Java source, Kotlin source, Groovy-compatible source, or bytecode using Micronaut Sourcegen.

## Integration Model

Micronaut Sourcegen has two layers:

- Model layer: `sourcegen-model` and `sourcegen-generator` provide `ObjectDef`, `TypeDef`, `MethodDef`, `ExpressionDef`, `StatementDef`, `VariableDef`, `SourceGenerator`, and `SourceGenerators`.
- Backend layer: `sourcegen-generator-java`, `sourcegen-generator-kotlin`, and `sourcegen-generator-bytecode` turn the same model into generated output.

Normal consuming modules should build `ObjectDef` models and let a backend emit them. Backend modules may use JavaPoet, KotlinPoet, or ASM internally; visitors in other modules should not.

## Backend Selection

Choose the backend by compilation path:

- Java source: use `sourcegen-generator-java`; it provides `JavaPoetSourceGenerator` for `VisitorContext.Language.JAVA`.
- Groovy-compatible source: use `sourcegen-generator-java`; it also registers `GroovyPoetSourceGenerator` for `GROOVY`.
- Kotlin source: use `sourcegen-generator-kotlin`; it provides `KotlinPoetSourceGenerator` for `KOTLIN`.
- Bytecode: use `sourcegen-generator-bytecode`; it provides `ByteCodeGenerator`, advertises `JAVA`, and writes class files with `VisitorContext.visitClass`.

Do not place `sourcegen-generator-java` and `sourcegen-generator-bytecode` together on the same Java annotation-processor path unless the module explicitly controls generator selection. Both provide a `JAVA` backend.

## Gradle Wiring

Generator modules usually need the model and generator lookup APIs:

```kotlin
dependencies {
    implementation(projects.sourcegenGenerator)
}
```

Java source consumers use annotation processing:

```kotlin
dependencies {
    annotationProcessor(projects.sourcegenGeneratorJava)
    annotationProcessor(projects.myCustomGenerators)
}
```

Kotlin consumers use KSP:

```kotlin
dependencies {
    ksp(projects.sourcegenGeneratorKotlin)
    ksp(projects.myCustomGeneratorsKotlin)
    ksp(projects.myCustomGenerators)
}
```

Bytecode consumers use the bytecode generator on the Java annotation processor path:

```kotlin
dependencies {
    annotationProcessor(projects.sourcegenGeneratorBytecode)
    annotationProcessor(projects.myCustomGenerators)
}
```

Use the current repository's catalog aliases and project accessors. In other Micronaut modules, map these examples to that module's catalog names and scopes.

## Visitor Pattern

Use one visitor implementation to build language-neutral models:

```java
@Internal
public final class GenerateExampleVisitor implements TypeElementVisitor<GenerateExample, Object> {

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
        if (sourceGenerator == null) {
            return;
        }

        ClassDef generated = ClassDef.builder(element.getPackageName() + ".GeneratedExample")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodDef.builder("message")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.STRING)
                .build((aThis, params) -> ExpressionDef.constant("ok").returning()))
            .build();

        sourceGenerator.write(generated, context, element);
    }
}
```

Use `VisitorKind.ISOLATING` when output depends only on the visited element. Use the originating element in `write(...)` so incremental processing remains accurate.

## Do Not Instantiate DSL Records Directly

Always use Sourcegen builders, factory methods, or named helper methods to create model objects:

- Use `ClassDef.builder("pkg.Name")`, not a `ClassDef` constructor.
- Use `TypeDef.parameterized(List.class, String.class)`, not `new ClassTypeDef.Parameterized(...)`.
- Use `TypeDef.STRING.array()` or `TypeDef.array(TypeDef.STRING)`, not `new TypeDef.Array(...)`.
- Use `ExpressionDef.constant("x")` or `TypeDef.Primitive.INT.constant(1)`, not `new ExpressionDef.Constant(...)`.
- Use `expr.returning()`, `StatementDef.multi(...)`, `StatementDef.doTry(...)`, `condition.doIf(...)`, and switch helpers, not direct `StatementDef` record constructors.
- Use `expr.newLocal("name", local -> ...)` or a project helper for locals instead of spreading direct `VariableDef.Local` construction.

If a model construct has no public helper yet, add a small helper/factory near the generator code or improve the Sourcegen API before repeating direct record construction in consuming modules. This keeps module code stable when record internals evolve.

## Type Modeling

Common type helpers:

```java
TypeDef stringType = TypeDef.STRING;
TypeDef intType = TypeDef.Primitive.INT;
TypeDef nullableString = TypeDef.STRING.makeNullable();
TypeDef stringArray = TypeDef.STRING.array();
ClassTypeDef generatedType = ClassTypeDef.of("example.Generated");
ClassTypeDef listOfString = TypeDef.parameterized(List.class, String.class);
TypeDef.TypeVariable variableT = TypeDef.variable("T", TypeDef.of(CharSequence.class));
```

Prefer Micronaut AST metadata in visitors:

```java
TypeDef fieldType = TypeDef.of(fieldElement.getGenericType());
ClassTypeDef ownerType = ClassTypeDef.of(classElement);
MethodDef overridden = MethodDef.override(methodElement)
    .build((aThis, params) -> params.get(0).returning());
```

Use `ClassTypeDef.of("pkg.Generated")` for generated types that are not compiled yet.

## Generated Type Patterns

Class with field and constructor:

```java
FieldDef nameField = FieldDef.builder("name", TypeDef.STRING)
    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
    .build();

ClassDef generated = ClassDef.builder("example.PersonView")
    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
    .addField(nameField)
    .addAllFieldsConstructor(Modifier.PUBLIC)
    .addMethod(MethodDef.builder("name")
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeDef.STRING)
        .build((aThis, params) -> aThis.field(nameField).returning()))
    .build();
```

Interface:

```java
InterfaceDef repository = InterfaceDef.builder("example.Repository")
    .addModifiers(Modifier.PUBLIC)
    .addTypeVariable(TypeDef.variable("T"))
    .addMethod(MethodDef.builder("find")
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .addParameter("id", TypeDef.Primitive.LONG)
        .returns(TypeDef.variable("T"))
        .build())
    .build();
```

Record:

```java
RecordDef record = RecordDef.builder("example.PersonRecord")
    .addModifiers(Modifier.PUBLIC)
    .addProperty(PropertyDef.builder("name")
        .addModifiers(Modifier.PUBLIC)
        .ofType(TypeDef.STRING)
        .build())
    .addProperty(PropertyDef.builder("age")
        .addModifiers(Modifier.PUBLIC)
        .ofType(TypeDef.Primitive.INT)
        .build())
    .build();
```

Enum:

```java
EnumDef status = EnumDef.builder("example.Status")
    .addModifiers(Modifier.PUBLIC)
    .addEnumConstant("NEW", TypeDef.Primitive.INT.constant(0))
    .addEnumConstant("DONE", TypeDef.Primitive.INT.constant(1))
    .addField(FieldDef.builder("code", TypeDef.Primitive.INT)
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .build())
    .addAllFieldsConstructor(Modifier.PRIVATE)
    .build();
```

Annotation type:

```java
AnnotationObjectDef annotation = AnnotationObjectDef.builder("example.GeneratedMarker")
    .addModifiers(Modifier.PUBLIC)
    .addAnnotation(AnnotationDef.builder(Retention.class)
        .addMember("value", RetentionPolicy.RUNTIME)
        .build())
    .addMember(AnnotationObjectDef.AnnotationMemberDef.builder("value", TypeDef.STRING)
        .withDefault(ExpressionDef.constant(""))
        .build())
    .build();
```

## Referencing Generated Members

When a generator defines a field or method and then uses that member in generated code, keep the `FieldDef` or `MethodDef` in a variable and reuse it. This keeps the generated member name, type, parameters, return type, annotations, and modifiers in one model object and avoids drift between the declaration and body code.

Prefer this:

```java
FieldDef nameField = FieldDef.builder("name", TypeDef.STRING)
    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
    .build();

MethodDef normalizeMethod = MethodDef.builder("normalize")
    .addModifiers(Modifier.PRIVATE)
    .addParameter("value", TypeDef.STRING)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> params.get(0)
        .invoke("trim", TypeDef.STRING)
        .returning());

MethodDef displayMethod = MethodDef.builder("display")
    .addModifiers(Modifier.PUBLIC)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> aThis.invoke(
        normalizeMethod,
        aThis.field(nameField)
    ).returning());

ClassDef generated = ClassDef.builder("example.GeneratedPerson")
    .addField(nameField)
    .addMethod(normalizeMethod)
    .addMethod(displayMethod)
    .build();
```

Avoid repeating member metadata when a model instance exists:

```java
// Avoid when `nameField` is available.
aThis.field("name", TypeDef.STRING);

// Avoid when `normalizeMethod` is available.
aThis.invoke("normalize", List.of(TypeDef.STRING), TypeDef.STRING, List.of(aThis.field(nameField)));
```

The same rule applies to static members:

```java
ClassTypeDef generatedType = ClassTypeDef.of("example.GeneratedPerson");

FieldDef defaultName = FieldDef.builder("DEFAULT_NAME", TypeDef.STRING)
    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
    .initializer(ExpressionDef.constant("unknown"))
    .build();

MethodDef createMethod = MethodDef.builder("create")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .returns(TypeDef.STRING)
    .buildStatic(params -> generatedType.getStaticField(defaultName).returning());

MethodDef displayDefaultMethod = MethodDef.builder("displayDefault")
    .addModifiers(Modifier.PUBLIC)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> generatedType.invokeStatic(
        createMethod
    ).returning());

ClassDef generated = ClassDef.builder("example.GeneratedPerson")
    .addField(defaultName)
    .addMethod(createMethod)
    .addMethod(displayDefaultMethod)
    .build();
```

Avoid the string-based equivalent when the generated `MethodDef` is available:

```java
generatedType.invokeStatic(
    "create",
    TypeDef.STRING
);
```

## Referencing Known Classpath Members

When the generated code targets a known method or field that is accessible reflectively and present on the annotation processor classpath, prefer a reflective handle over the string-based Sourcegen overloads. This uses reflection at generation time only to describe a known member; it does not generate runtime reflection.

Use `ReflectionUtils.getRequiredMethod(...)` for instance methods:

```java
private static final Method STRING_TRIM =
    ReflectionUtils.getRequiredMethod(String.class, "trim");

MethodDef trim = MethodDef.builder("trim")
    .addParameter("value", TypeDef.STRING)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> params.get(0)
        .invoke(STRING_TRIM)
        .returning());
```

Use `ReflectionUtils.getRequiredMethod(...)` for static methods:

```java
private static final Method INTEGER_PARSE_INT =
    ReflectionUtils.getRequiredMethod(Integer.class, "parseInt", String.class);

MethodDef parse = MethodDef.builder("parse")
    .addParameter("value", TypeDef.STRING)
    .returns(TypeDef.Primitive.INT)
    .buildStatic(params -> ClassTypeDef.of(Integer.class)
        .invokeStatic(INTEGER_PARSE_INT, params.get(0))
        .returning());
```

Use `ReflectionUtils.getRequiredField(...)` for known static fields:

```java
private static final Field CASE_INSENSITIVE_ORDER =
    ReflectionUtils.getRequiredField(String.class, "CASE_INSENSITIVE_ORDER");

MethodDef comparator = MethodDef.builder("comparator")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .returns(TypeDef.of(Comparator.class))
    .buildStatic(params -> ClassTypeDef.of(String.class)
        .getStaticField(CASE_INSENSITIVE_ORDER)
        .returning());
```

For known instance fields, centralize the current field-name/type bridge in a helper fed by a reflective `Field`:

```java
private static final Field HOLDER_VALUE =
    ReflectionUtils.getRequiredField(Holder.class, "value");

private static VariableDef.Field instanceField(ExpressionDef instance, Field field) {
    return instance.field(field.getName(), TypeDef.of(field.getType()));
}

MethodDef setValue = MethodDef.builder("setValue")
    .addParameter("holder", ClassTypeDef.of(Holder.class))
    .addParameter("value", TypeDef.STRING)
    .returns(TypeDef.VOID)
    .buildStatic(params -> instanceField(params.get(0), HOLDER_VALUE)
        .assign(params.get(1)));
```

Prefer AST `FieldElement` metadata for fields discovered from the visited source element. Prefer generated `FieldDef`/`MethodDef` instances for members the current generator defines.

## Method Bodies

Return expression:

```java
MethodDef message = MethodDef.builder("message")
    .returns(TypeDef.STRING)
    .build((aThis, params) -> ExpressionDef.constant("hello").returning());
```

Local variable through helper:

```java
MethodDef upper = MethodDef.builder("upper")
    .addParameter("value", TypeDef.STRING)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> params.get(0).newLocal("local", local ->
        local.invoke("toUpperCase", TypeDef.STRING).returning()
    ));
```

Field assignment:

```java
MethodDef setName = MethodDef.builder("setName")
    .addParameter("name", TypeDef.STRING)
    .returns(TypeDef.VOID)
    .build((aThis, params) -> aThis.field(nameField).assign(params.get(0)));
```

Try/finally:

```java
MethodDef withLock = MethodDef.builder("withLock")
    .addParameter("lock", ClassTypeDef.of(Lock.class))
    .returns(TypeDef.STRING)
    .build((aThis, params) -> StatementDef.multi(
        params.get(0).invoke("lock", TypeDef.VOID),
        StatementDef.doTry(ExpressionDef.constant("done").returning())
            .doFinally(params.get(0).invoke("unlock", TypeDef.VOID))
    ));
```

Try/catch with multiple catches:

```java
MethodDef parse = MethodDef.builder("parse")
    .addParameter("value", TypeDef.STRING)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> {
        ExpressionDef parsed = ClassTypeDef.of(Integer.class)
            .invokeStatic(
                "parseInt",
                List.of(TypeDef.STRING),
                TypeDef.Primitive.INT,
                List.of(params.get(0))
            );
        return StatementDef.doTry(
            ClassTypeDef.of(String.class)
                .invokeStatic(
                    "valueOf",
                    List.of(TypeDef.Primitive.INT),
                    TypeDef.STRING,
                    List.of(parsed)
                )
                .returning()
        ).doCatch(NumberFormatException.class, exception ->
            ExpressionDef.constant("invalid number").returning()
        ).doCatch(IllegalArgumentException.class, exception ->
            exception.invoke("getMessage", TypeDef.STRING).returning()
        );
    });
```

Try/catch/finally with multiple catches:

```java
MethodDef parseWithCleanup = MethodDef.builder("parseWithCleanup")
    .addParameter("value", TypeDef.STRING)
    .addParameter("cleanup", ClassTypeDef.of(Runnable.class))
    .returns(TypeDef.STRING)
    .build((aThis, params) -> {
        ExpressionDef parsed = ClassTypeDef.of(Integer.class)
            .invokeStatic(
                "parseInt",
                List.of(TypeDef.STRING),
                TypeDef.Primitive.INT,
                List.of(params.get(0))
            );
        return StatementDef.doTry(
            ClassTypeDef.of(String.class)
                .invokeStatic(
                    "valueOf",
                    List.of(TypeDef.Primitive.INT),
                    TypeDef.STRING,
                    List.of(parsed)
                )
                .returning()
        ).doCatch(NumberFormatException.class, exception ->
            ExpressionDef.constant("invalid number").returning()
        ).doCatch(RuntimeException.class, exception ->
            ExpressionDef.constant("runtime failure").returning()
        ).doFinally(
            params.get(1).invoke("run", TypeDef.VOID)
        );
    });
```

Synchronized block:

```java
private static StatementDef synchronizedBlock(ExpressionDef monitor, StatementDef body) {
    return new StatementDef.Synchronized(monitor, body);
}

MethodDef synchronizedRead = MethodDef.builder("synchronizedRead")
    .addParameter("monitor", TypeDef.OBJECT)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> synchronizedBlock(
        params.get(0),
        ExpressionDef.constant("locked").returning()
    ));
```

`StatementDef.Synchronized` currently has no public static factory. Keep direct construction inside one named helper instead of scattering record construction through generated method bodies.

Super constructor:

```java
MethodDef constructor = MethodDef.constructor()
    .addModifiers(Modifier.PUBLIC)
    .addParameter("name", TypeDef.STRING)
    .build((aThis, params) -> aThis.superRef(parentType).invokeSuperConstructor(params.get(0)));
```

## Invocations and Static Fields

Instance invocation:

```java
ExpressionDef length = params.get(0).invoke("length", TypeDef.Primitive.INT);
```

Static invocation with explicit parameter types:

```java
ExpressionDef valueOf = ClassTypeDef.of(String.class)
    .invokeStatic("valueOf", List.of(TypeDef.OBJECT), TypeDef.STRING, List.of(params.get(0)));
```

Static field argument:

```java
ClassTypeDef standardCharsets = ClassTypeDef.of(StandardCharsets.class);
ClassTypeDef charset = ClassTypeDef.of(Charset.class);

ExpressionDef bytes = params.get(0).invoke(
    "getBytes",
    List.of(charset),
    TypeDef.Primitive.BYTE.array(),
    List.of(standardCharsets.getStaticField("UTF_8", charset))
);
```

Enum static field:

```java
ClassTypeDef timeUnit = ClassTypeDef.of(TimeUnit.class);
ExpressionDef seconds = timeUnit.getStaticField("SECONDS", timeUnit);
```

Class-literal style value for annotation members:

```java
ExpressionDef stringClass = ClassTypeDef.of(String.class)
    .getStaticField("class", TypeDef.CLASS);
```

The second argument to `getStaticField` is the field value type, not necessarily the owner type.

## Switches

String switches are supported for both expression switches and statement switches. Model each string case with `ExpressionDef.constant("value")`; model the default as the explicit default expression or as an `ExpressionDef.nullValue()` case in a statement switch.

Expression switch:

```java
MethodDef code = MethodDef.builder("code")
    .addParameter("name", TypeDef.STRING)
    .returns(TypeDef.Primitive.INT)
    .build((aThis, params) -> params.get(0).asExpressionSwitch(
        TypeDef.Primitive.INT,
        Map.of(
            ExpressionDef.constant("a"), TypeDef.Primitive.INT.constant(1),
            ExpressionDef.constant("b"), TypeDef.Primitive.INT.constant(2)
        ),
        TypeDef.Primitive.INT.constant(0)
    ).returning());
```

String switch returning an enum/static field:

```java
ClassTypeDef timeUnit = ClassTypeDef.of(TimeUnit.class);

MethodDef unit = MethodDef.builder("unit")
    .addParameter("name", TypeDef.STRING)
    .returns(timeUnit)
    .build((aThis, params) -> params.get(0).asExpressionSwitch(
        timeUnit,
        Map.of(
            ExpressionDef.constant("seconds"), timeUnit.getStaticField("SECONDS", timeUnit),
            ExpressionDef.constant("minutes"), timeUnit.getStaticField("MINUTES", timeUnit)
        ),
        timeUnit.getStaticField("HOURS", timeUnit)
    ).returning());
```

Statement switch:

```java
MethodDef codeStatement = MethodDef.builder("codeStatement")
    .addParameter("name", TypeDef.STRING)
    .returns(TypeDef.Primitive.INT)
    .build((aThis, params) -> params.get(0).asStatementSwitch(
        TypeDef.Primitive.INT,
        Map.of(
            ExpressionDef.constant("a"), TypeDef.Primitive.INT.constant(1).returning(),
            ExpressionDef.constant("b"), TypeDef.Primitive.INT.constant(2).returning(),
            ExpressionDef.nullValue(), TypeDef.Primitive.INT.constant(0).returning()
        )
    ));
```

String statement switch with a default case:

```java
MethodDef label = MethodDef.builder("label")
    .addParameter("name", TypeDef.STRING)
    .returns(TypeDef.STRING)
    .build((aThis, params) -> params.get(0).asStatementSwitch(
        TypeDef.STRING,
        Map.of(
            ExpressionDef.constant("a"), ExpressionDef.constant("alpha").returning(),
            ExpressionDef.constant("b"), ExpressionDef.constant("beta").returning(),
            ExpressionDef.nullValue(), ExpressionDef.constant("unknown").returning()
        )
    ));
```

Switch expressions require a default expression. Statement switches should handle the default intentionally.

## Arrays and Annotations

Array creation:

```java
TypeDef.Array stringArray = TypeDef.STRING.array();
ExpressionDef values = stringArray.instantiate(
    ExpressionDef.constant("a"),
    ExpressionDef.constant("b")
);
```

Element and type-use annotations:

```java
AnnotationDef min = AnnotationDef.builder(ClassTypeDef.of("jakarta.validation.constraints.Min"))
    .addMember("value", 1)
    .build();

TypeDef numbersType = TypeDef.parameterized(
    ClassTypeDef.of(List.class),
    TypeDef.Primitive.INT.wrapperType().annotated(min)
);
```

Copy existing annotations with `AnnotationDef.of(annotationValue, visitorContext)` when preserving AST annotation metadata.

## Lambdas

Use functional interface metadata and `getLambda()`:

```java
InterfaceDef stringFunction = InterfaceDef.builder("example.StringFunction")
    .addModifiers(Modifier.PUBLIC)
    .addAnnotation(FunctionalInterface.class)
    .addMethod(MethodDef.builder("apply")
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .addParameter(TypeDef.STRING)
        .returns(TypeDef.STRING)
        .build())
    .build();

ExpressionDef lambda = stringFunction.asTypeDef()
    .getLambda()
    .implement((aThis, params) ->
        params.get(0).invoke("substring", TypeDef.STRING, ExpressionDef.constant(1)).returning()
    );
```

For generic functional interfaces, resolve type variables when calling `getLambda(resolveVariableFn)`.

## Bytecode Notes

`ByteCodeGenerator` writes class files and does not support `write(ObjectDef, Writer)`. In consuming modules, use it through annotation processing and `SourceGenerator.write(objectDef, context, element)`.

When working directly on `sourcegen-bytecode-writer`, use writer-specific tests and keep model semantics consistent with source backends. Bytecode gaps should be handled by adding writer support or narrowing generated model usage, not by bypassing Sourcegen in consuming modules.

## Testing Matrix

Pick tests based on backend:

- Java source: compile the generator and Java consuming module; run tests that import or instantiate generated Java types.
- Kotlin source: compile the generator and Kotlin consuming module with KSP; run Kotlin tests that use generated Kotlin types.
- Bytecode: compile the generator and bytecode consuming module; run runtime tests plus bytecode writer tests if the construct touches bytecode-specific behavior.
- Shared model behavior: add or update tests in `sourcegen-model` and at least one backend test.
- Multi-backend feature: verify Java, Kotlin, and bytecode where the construct is expected to be supported.

For this repository, follow `AGENTS.md` command conventions: Gradle wrapper only, quiet for non-test tasks, non-quiet for test tasks, targeted tests before full module tests, then `./gradlew -q cM` and Spotless when new files are added.

## Common Pitfalls

- Multiple `JAVA` Sourcegen backends on one annotation-processor path can make `findByLanguage(JAVA)` ambiguous.
- Direct DSL record construction leaks internal representation into consuming modules; use factories/builders/helpers.
- Static field expressions need the field value type.
- Invocation models validate argument counts; use explicit parameter types for overloaded methods.
- Enum constants with constructor args require matching private constructors.
- `ClassTypeDef.of(Class<?>)` rejects primitive classes; use `TypeDef.of(int.class)` or `TypeDef.Primitive.INT`.
- Kotlin generated output may need KSP-compatible processor wiring even when the shared visitor is Java.
