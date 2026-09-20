# Python metadata backends: resolved model, build-time and runtime generation

This module holds the resolved metadata model of a Python class, its codec, and the generator both metadata
backends of the Python compiler share. In the **runtime** backend it is also the application runtime: it
discovers the saved models through catalogs, exposes lightweight references for candidate selection, and
generates the definition and introspection classes as real JVM classes on first use, in the wrapper's class
loader and package, without javac, the Python compiler, the AST API (`core-processor`) or Sourcegen.

It supersedes the prototype of commit `319c9055cb` (`codex/python-runtime-metadata-prototype`), whose gaps it
closes as far as the first delivery batch of `IMPLEMENTATION_PLAN.md` (changes 1-4) goes; the remaining changes
of that plan are listed at the end.

## Selecting a backend

The compiler option `micronaut.python.metadata.backend` selects the backend; the default is unchanged.

| Value | What the compilation emits for a selected Python class |
|---|---|
| `compiler` (default) | The existing `BeanDefinitionWriter` / `BeanIntrospectionWriter` classes. |
| `model-build-time` | The saved model (`META-INF/micronaut/python/runtime/<class>.mpym`) **and** the `$Definition` / `$Introspection` classes the shared generator produces from it, with their service entries. Deployable like the compiler's output, including in native images. |
| `model-runtime` | The saved model and one discovery catalog per compilation (`META-INF/micronaut/python/runtime/catalog`). No metadata class and no service entry: the application generates the classes. |

`micronaut.python.metadata.types` restricts the model backends to the listed classes (`app.Bean`) or packages
(`app.*`); without it every Python class of the compilation is selected. The prototype's
`micronaut.python.runtimeMetadata=<classes>` still selects the runtime backend for the listed classes.

```java
PyronautCompiler.builder()
    .pythonSrc(sourceDirectory)
    .targetDir(outputDirectory)
    .options(List.of("-Amicronaut.python.metadata.backend=model-runtime"))
    .build()
    .compile();
```

For the runtime backend, add `micronaut-context-python-runtime` to the application's runtime dependencies.
Nothing else is wired by hand: a service-loaded `ApplicationContextConfigurer` composes the runtime references
into whatever `BeanDefinitionsProvider` the application configured, and installs the introspection provider, so
`BeanIntrospector.findIntrospections` and `findIntrospectedTypes` see the runtime introspections without
generating them. Known-class lookup also works through a `BeanIntrospectionFallback` before any context exists.

## What is generated, and how

```text
Python + Java symbol analysis + annotation/type/bean visitors      (unchanged)
                         |
   PythonBeanDefinitionProcessor, after every visitor ran
                         |
   BeanDefinitionCreatorFactory.produce(...)   PropertyElementQuery.of(...) + getBeanProperties
   with a *recording* builder factory           (the introspection visitor's own analysis)
                         |
             resolved model (records, names and descriptors only)
                         |
                 PythonMetadataCodec: .mpym, versioned, checksummed, deterministic
                         |
           +-------------+----------------+
   model-build-time                    model-runtime
   PythonMetadataClassGenerator        catalog + PythonRuntimeBeanReference / PythonRuntimeIntrospectionReference
   -> class files + service entries       -> PythonMetadataClassGenerator on first load -> defineClass in the wrapper's loader
```

- **The same analysis as the compiler backend.** Bean definitions are derived by `BeanDefinitionCreatorFactory`,
  the entry point every language module uses, through `ModelBeanDefinitionBuilderFactory`, an
  `ElementBeanDefinitionBuilderFactory` that records the constructor, injection points, lifecycle methods and
  exposed types instead of emitting bytecode. Introspections use the visitor's `PropertyElementQuery` and merged
  property metadata, and record the property indexes visitors contribute (`@Introspected(indexed = ...)`, which
  micronaut-validation adds to every introspected class). The model is captured in the bean definition phase, so
  everything the annotation, type and bean visitors changed before is in it.
- **The same runtime contracts as the compiler backend.** The generated classes extend
  `AbstractInitializableBeanDefinitionAndReference` and `AbstractInitializableBeanIntrospectionAndReference`, are
  named `<package>.$<Class>$Definition` / `$Introspection` like the compiler's, and invoke the wrapper's
  constructor, injected methods, lifecycle callbacks and property accessors directly from the recorded
  descriptors. Dependency resolution, qualifiers, conditions, scopes, environment configuration and disposal are
  the framework's: the generated `instantiate` / `inject` / `initialize` / `dispose` call the same
  `getBeanForConstructorArgument`, `getBeanForMethodArgument`, `getBeansOfType...`, `findBean...`,
  `getPropertyPlaceholderValue...`, `postConstruct` and `preDestroy` helpers the compiler backend calls.
- **One generator, byte for byte.** `PythonMetadataClassGenerator` is a pure function of the model. The
  build-time backend writes its output to disk; the runtime backend defines the same bytes. The parity harness
  asserts that the class files of a `model-build-time` compilation equal what the runtime generates from the
  `model-runtime` models of the same sources, and that the models of both compilations are identical.
- **Initialization from the model, in both backends.** A generated class initializes itself through
  `PythonMetadataSupport.definitionState(Bean.class)` / `introspectionState(Bean.class)`: annotation metadata,
  `MethodReference`s, `PrecalculatedInfo`, `Argument`s, `BeanPropertyRef`s, qualifiers and exposed types are
  materialized once per wrapper class from the saved model, so a class written at build time and a class defined
  at runtime run the same code path.

## Runtime loading and caching

- One `Holder` per wrapper class, kept in a `ClassValue`, so it lives and dies with the class and its loader; no
  map retains an application class loader.
- The holder serializes the first generation of each artifact. `defineClass` runs once; if the class initializes
  or instantiates badly afterwards the failure is recorded and reported again on every later request, the class
  is never defined a second time. Concurrent first lookups get one class and, for introspections, one instance.
- Generation errors are `PythonMetadataGenerationException`s naming the class, the model identity (the SHA-256
  of the saved bytes) and the failing operation (reading, defining, initializing, instantiating).
- A reference's `load()` returns a new definition per context, like the compiler's; conditions, evaluated
  expressions and qualifiers therefore never leave the context that evaluated them. Introspections are
  context-free and shared.
- Catalogs of several class-path entries are merged in class-path order; a class listed twice with the same
  model identity is one class, with different identities a conflict that fails discovery with both sources named.
  A class that has both a build-time definition and a runtime model fails discovery too.
- Discovery reads no model and generates nothing by itself: a context start generates only what an eagerly
  initialized bean needs, and nothing at all when there is none (asserted by the parity harness). Definitions are
  generated when candidate resolution loads them. A `@Context` bean loads its definition at startup to evaluate its
  conditions, whether or not they hold.

## Supported

Public top-level Python classes, ordinary class-path class loaders. Bean definitions: constructor injection of
beans (qualified by `@Named`, `@Any`, qualifier annotations, repeatable qualifiers, interceptor bindings or
`@Type`), collections and arrays of beans, maps and streams of beans, bean registrations, `Optional` beans,
`@Value` placeholders, `@Property` values, `BeanContext` and `BeanResolutionContext`; `@Inject` method injection
(required or not); `@PostConstruct` and `@PreDestroy`; every scope and `@Primary`; `@Requires` conditions; exposed
types and candidate selection; `@Context` eager initialization; iterable beans (`@EachProperty`, `@EachBean`), whose values are supplied by name
when the definition is instantiated and whose configuration path is injected from the resolution context; type
arguments of generic super types; executable methods without interception
(`@Executable` and its stereotypes such as `@Controller` routes, `processOnStartup`), generated as the definition's
`$Exec` companion. `@Factory` classes: each `@Bean` method produces its own definition, named as the compiler names
it (`$Factory$Method<n>$Definition`), instantiated by looking the factory bean up through the resolution context
(qualified by the factory class) and invoking the method, with the method's own annotation metadata over the
factory class's, its parameters as injection points, `@Bean(preDestroy = ...)` lifecycle methods and
`@Bean(typed = ...)` exposed types. A Python attribute annotated with `@Bean` is a factory method of its accessor,
so a factory field, which the language cannot express, stays rejected. A `@Named` without a value takes its implicit value, as the writer applies it:
the decapitalized simple name of a class, or the name of a factory method, injected method parameter or
constructor parameter. Introspections: constructors with arguments, readable, writable and read-only properties of
any type, generics, property annotation metadata, property indexes, `@Executable` bean methods, enums with their
constants, static `@Creator` methods, `@Introspected(constructors = true)` and `@Introspected(members = true)`. `@ConfigurationProperties` beans: property
binding through setters, each guarded by the configuration containing the property, the whole binding skipped when it
holds none of them, the command line property of a `cliPrefix` configuration, and the writer's validation decision
(the definition is validated, and the constructed bean is validated as a whole when it binds configuration).

Two constructs are written by the compiler, in every backend. A class that needs interception (an around proxy, an
introduction proxy, or a method adapted to an interface, as an `@EventListener` is) needs a proxy class, and a class
with a `@ConfigurationBuilder` needs the builder wired from the configuration as the bean is injected. Neither is
described by the model, so the bean definitions of such a class, and the proxy they need, are written by the
compiler's writers, while its introspection is still described by its model. The fallback is per class and silent:
such a class compiles and behaves the same under every backend, which the parity harness asserts.

Two more artifacts are not described by the model either: the introspection a validated configuration class needs
and the converter of an introspected enum, both added to Java classes generated for the Python class and written by
the ordinary Java pipeline. The output of the runtime backend therefore contains those classes, the definitions of
the classes that need interception, and their service entries (all asserted by the parity harness), and nothing
else of the metadata.

Not supported yet, and reported at compile time by a diagnostic naming the construct and the class (no silent
fallback): evaluated expressions, `@InjectScope`, field injection,
reflection-requiring members, suspending methods, `@Introspected(classes, classNames, packages, builder, targetPackage)`,
which all ask for the introspection of another class, and incremental compilation of the model backends. The model
describes one class, and the runtime backend defines a generated class in the package and loader of the class it
describes, so an introspection of another class or in another package has no model to be described by and no class
to be defined next to.

## Tests

- `PythonRuntimeMetadataSpec` (inject-python-test): a `model-runtime` compilation emits models and a catalog but
  no definition, introspection or service entry; an ordinary application discovers, generates and uses them
  (injection, values, lifecycle, enumeration); concurrent first lookup generates one class; separate loaders stay
  isolated; a fresh JVM with `--limit-modules` (no `jdk.compiler`) and only the runtime modules on its class path
  generates from disk and proves the absence of the Python compiler, javac, the AST API, Sourcegen and JavaParser;
  an unsupported construct is a compilation error.
- `PythonMetadataBackendParitySpec`: the same fixtures compiled by the three backends; inventories, byte-identical
  generated classes (definitions, their `$Exec` companions and introspections), observable metadata of every
  definition and introspection, and application behaviour, including a controller route served over HTTP,
  executable method invocation and startup processing.
- `PythonMetadataClassGeneratorTest`, `PythonMetadataCodecTest` (this module): the generated classes verify
  against real classes; generation is deterministic; the codec round-trips and reports corruption, truncation,
  unknown versions and mismatches.
- `PythonMetadataBenchmarkSpec`: opt-in measurements (`-Dpython.metadata.benchmark=true`), see
  `PYTHON_METADATA_BENCHMARK.md`.

## Remaining plan

Changes 5 (complete discovery across mixed build-time / runtime artifacts is done for what is generated; what
remains is qualification against third-party providers), 6b beyond factory methods (associated beans, configuration binding,
`EachBean` / `EachProperty`, event adapters), 6c (builders, enums, static creators, described members), the AOP
part of 6d, 7 (incremental compilation, mode transitions and output cleanup) and 8 (documented opt-in release,
native-image selection, performance report) of `IMPLEMENTATION_PLAN.md` remain open. 6a and the non-intercepted
part of 6d are covered, as are the factory methods of 6b.
