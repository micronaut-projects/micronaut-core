# Python runtime metadata prototype

This isolated prototype defers generation of selected Python bean definitions and
introspections until application runtime. It writes **real JVM classes at runtime**;
it does not hide build-time bytecode in resources or run javac at application startup.

Based on `claude/kind-euler-2nyv6v` snapshot `c71342b8df`.

## Run the experiment

From this checkout:

```sh
./gradlew :micronaut-inject-python-test:test \
  --tests '*RuntimePythonMetadataSpec' -Ppython-ci
```

The test compiles two Python fixtures to disk. It verifies that their ordinary
`$Definition` and `$Introspection` classes do not exist, then resolves a singleton
and uses generated constructor/getter/setter dispatch. It also exercises concurrent
lookup, separate classloaders, and rejection of unsupported lifecycle metadata.
A separate JVM excludes `jdk.compiler`, uses only the runtime module and its runtime
dependencies, verifies that javac, the Python compiler and Java annotation processor are absent, and repeats the
application-context and introspection checks using the files on disk.

To opt in for a full compilation, pass the exact JVM wrapper names:

```java
PyronautCompiler.builder()
    .pythonSrc(sourceDirectory)
    .targetDir(outputDirectory)
    .options(List.of("-Amicronaut.python.runtimeMetadata=app.RuntimeBean,app.RuntimePerson"))
    .build()
    .compile();
```

Add `micronaut-context-python-runtime` to the application's runtime dependencies
and install its bean provider:

```java
ApplicationContext.builder()
    .classLoader(applicationClassLoader)
    .beanDefinitionsProvider(new RuntimePythonBeanDefinitionsProvider())
    .start();
```

`BeanIntrospector.forClassLoader(applicationClassLoader).getIntrospection(type)`
uses the module's service-registered fallback automatically.

## Compiler/runtime boundary

1. Compile-time validation accepts only the supported subset. The compiler emits
   `META-INF/micronaut/python/runtime/index` and a versioned `.properties` model
   per selected class. Existing Python JVM wrappers are still compiled normally.
2. The normal Python bean-definition writer and introspection visitor skip selected
   types. Unselected classes use the existing compilation path.
3. The provider combines ordinary Java references with lightweight Python references.
   Reading the index does not generate metadata bytecode or start a Python context.
4. A reference's `load()` generates its bean-definition subclass with Sourcegen.
   The introspection fallback independently generates its introspection subclass
   on first lookup. Micronaut may load definitions while starting the context.
5. The generated code invokes wrapper constructors and property methods directly.
   Model construction resolves method signatures using reflection; subsequent
   generated property access does not use reflective invocation.
6. `MethodHandles.Lookup.defineClass` defines the result in the wrapper's classloader
   and runtime package. A `ClassValue`-owned state serializes first generation and
   caches definitions/introspections independently. Different classloaders have
   different generated types and instances.

The runtime module currently needs `core-processor`: a fresh-JVM experiment showed
that Sourcegen links AST API classes even when using reflection-backed models.
This is a real runtime dependency in this prototype, not just a compile-only one.
No AST is reconstructed and no processor is invoked. Neither `inject-python` nor
`inject-java` belongs on the application classpath. The fresh-JVM test is the
acceptance gate for that separation. Extracting a small AST API or decoupling
Sourcegen is required before claiming a lightweight runtime backend.

## Deliberate limits

- Public, top-level, non-inherited Python classes with a public no-argument
  constructor and plain `@Introspected`; optionally `@Singleton`.
- Unannotated readable/writable `String`, `int`, and `boolean` properties only.
- No custom introspection options, qualifiers, conditions, injection, lifecycle
  annotations, executable metadata, validation or AOP. Unsupported inspected
  annotations and shapes fail compilation. This is not a complete metadata codec.
- The model captures resolved declarations before type visitors run. Metadata
  subsequently contributed by third-party visitors is outside this prototype;
  supporting it requires a broader metadata codec.
- Full compilation into a fresh output directory. Incremental compilation is
  rejected; cleanup when changing opt-in selection is not implemented.
- Known-class introspection lookup only. Introspection enumeration and indexed
  annotation/property searches are not implemented.
- Existing mapping adapters, wrappers, Java symbol lookup and javac compilation
  remain. The prototype does not demonstrate javac removal.
- JVM classpath applications; native-image runtime class definition and JPMS access
  restrictions are outside this experiment.
- No performance claim: generation is moved to startup/first lookup, not eliminated.
  The prototype also adds Sourcegen/ASM and core-processor to application dependencies.

## Review surface

All newly exposed runtime types are internal experimental implementation details.
No existing public method signatures change. The compiler option is opt-in and
unsupported as a production contract. A production design would need a complete
resolved-metadata format, discovery enumeration, incremental ownership, AOP and
injection support, and build/startup/memory comparisons before replacing AOT output.

## Verification recorded for this prototype

On OpenJDK 25.0.2 with GraalPy's fallback interpreter:

- Full compiler suite: **127 passed**.
- Full Python integration suite: **912 passed** (including the original five
  prototype checks).
- Final strengthened prototype suite: **5 passed**, including a real call into
  the Python-backed bean, stable definition identity, preserved class annotations,
  and a separate JVM in which `jdk.compiler` is not resolved.
- Runtime module `check` and `javadoc`: passed. Compiler Checkstyle and Spotless:
  passed. The unpublished runtime module has no binary compatibility baseline,
  so its baseline comparison is disabled.
- Compiler `japiCmp` against 5.2.2 reports an existing removed `ArgumentDef`
  constructor. A clean, uncached rebuild of the unmodified reviewed checkout
  reproduced the same failure; this prototype does not change that class.

The full suites preceded the final prototype-only refinements; the strengthened
five-test suite and affected style/documentation checks were rerun afterward.
The repository-wide `check` and full documentation site build were not run.
These are correctness results, not a performance comparison.

## Changed code and API scope

- `inject-python`: opt-in model writer plus hooks in the Python annotation,
  bean-definition and type-visitor processing paths. Ordinary compilation remains
  the default. One existing constant was reordered to satisfy Checkstyle.
- `context-python-runtime`: descriptor reader, bean provider, lazy bytecode backend,
  shared definition/introspection bases, service fallback and standalone smoke runner.
- `inject-python-test`: end-to-end spec and a separate application runtime classpath.
- `settings.gradle`: registers the new prototype module.

The new Java entry points are marked `@Internal`; no existing public API signature
is changed and no public API deprecation or migration is introduced. The module and
compiler option are prototype implementation details, not a proposed stable API.
