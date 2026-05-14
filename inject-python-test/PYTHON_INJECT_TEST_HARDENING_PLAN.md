# Python Inject Test Hardening Plan

## Objective

Raise Python inject test coverage to the same behavioral level as the Java,
Groovy, and Kotlin inject test suites where the behavior is language-neutral.
Keep this pass focused on tests, cataloging, and Gradle build reliability.

## Scope

Worktree:

* Path: `/Users/graemerocher/dev/micronaut/core.pyronaut.python-hardening`
* Branch: `python-hardening-tests`
* Base: `origin/python-support`

Source inventory is limited to direct subclasses of:

* `AbstractBeanDefinitionSpec`
* `AbstractTypeElementSpec`
* `AbstractKotlinCompilerSpec`

Source roots:

* `inject-groovy/src/test/groovy`
* `inject-groovy-test/src/main/groovy`
* `inject-java/src/test/groovy`
* `inject-java-test/src/test/groovy`
* `inject-java-test/src/main/groovy`
* `inject-kotlin/src/test/groovy`
* `inject-kotlin-test/src/main/groovy`

Python changes should stay under `inject-python-test` unless separately
approved.

The authoritative inventory commands and direct-subclass counts live in
`inject-python-test/DISABLED_TESTS.md`.

## Ground Rules

* Do not change production Python implementation in this pass.
* Add Python tests without asking for approval for each test.
* Use normal spec names that match the existing Python project style.
* Prefer adding coverage to an existing Python spec when ownership is clear.
* Attribute-backed Python classes expose `PropertyElement`, not `FieldElement`.
* Include idiomatic Python forms such as `str | None` when covering nullability.
* Use Spock `@PendingFeature` for portable behavior that is not currently
  implemented or cannot currently be represented by the Python compiler model.
* Use pending reasons in this format:
  `Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-XXXX`

## Migration Rules

Port tests that assert Micronaut behavior independent of Java, Groovy, or
Kotlin syntax:

* annotation metadata
* bean definitions and bean references
* introspection
* class elements, property elements, method elements, and constructor elements
* injection, qualifiers, providers, factories, and scopes
* AOP metadata and interceptor bindings
* generics and nullability
* visitor-visible structure and visitor-produced metadata

Do not port tests whose purpose is source-language-specific:

* Groovy AST quirks, closures, traits, dynamic property syntax, and Groovy
  reconstruction details
* Java records, package-info, package-private visibility, default interface
  methods, overload-only behavior, and Java parser diagnostics
* Kotlin data classes, companions, suspend functions, visibility, KSP behavior,
  and Kotlin reconstruction details
* expression-language specs inherited through intermediate expression fixtures

If Python already covers the same behavior, catalog it as covered instead of
duplicating the test.

## Catalog Rules

Maintain `inject-python-test/DISABLED_TESTS.md` as the decision log.

The catalog must include:

* inventory command list and direct-subclass counts
* migration rules
* pending Python tests with stable `PY-INJECT-XXXX` IDs
* intentionally unsupported source tests with reasons
* already covered equivalents and their Python spec locations

Every pending test must have a matching catalog row. Every intentionally
unsupported source behavior must have a reason.

## Work Loop

1. Mine a small source area from the direct-subclass inventory.
2. Decide whether the behavior is portable, already covered, pending, or
   intentionally unsupported.
3. Add or extend the most appropriate Python spec.
4. Add catalog entries immediately.
5. Run the focused Gradle test for the touched spec.
6. Run hygiene checks:

```bash
git diff --check
rg -n "par""ity|Par""ity|PY-PAR""ITY" inject-python-test settings.gradle
git status --short --branch
```

7. Stage and commit the verified slice before continuing.
8. Defer the full Python inject test task until the final sweep after all
   selected tests have been migrated and cataloged.

## Current Continuation State

As of May 14, 2026:

* Normal Gradle verification resolves the GraalPy `25.1.0-SNAPSHOT` bundle
  from the included checkout; do not rely on a local bundle override.
* The latest pending ID is `PY-INJECT-0088`; use `PY-INJECT-0089` for the next
  pending case.
* The most recent full verification passed with 400 tests and 65 skipped.
* Per-slice verification now uses focused tests only; run the full Python inject
  test task as a final sweep once the migration/catalog pass is complete.
* The most recent focused verification passed for `ConfigurationBuilderSpec`.
* Recent committed slices added or cataloged lifecycle hooks, lifecycle
  interceptor bindings, introduction-around coverage, and executable factory
  method inheritance coverage.

The latest completed source slices are:

* `ExecutableFactoryMethodSpec.groovy`: default interface method behavior is
  cataloged as unsupported, while multiple-interface factory executable
  behavior is covered by `FactoryBeanMethodSpec.test executable factory method
  with inherited client interface methods`.
* `AdviceDefinedOnFactorySpec.groovy`: class-level factory advice behavior is
  covered by `FactoryBeanMethodSpec.test class level around advice on factory
  applies to factory methods only`.
* `InterfaceIntroductionAdviceSpec.groovy`: type-level around advice and generic
  method signature gaps are pending as `PY-INJECT-0061` and `PY-INJECT-0062`,
  type-argument annotation propagation is pending as `PY-INJECT-0063`, and the
  generic/type-argument/method-annotation covered cases are cataloged.
* `IntroductionGenericTypesSpec.groovy`: generic introduced method signature
  gaps are pending as `PY-INJECT-0064` through `PY-INJECT-0066`, with JVM array
  portions cataloged as unsupported.
* `FactoryMappedAdviceSpec.groovy` and `FactoryMappedAdviceReflectionSpec.groovy`:
  Java annotation-mapped factory advice is pending as `PY-INJECT-0067`.
* `AnnotatedConstructorArgumentSpec.groovy`: constructor `@Value` metadata and
  class-level/method-level AOP invocation are covered; package-private method
  portions are cataloged as unsupported.
* `FinalModifierSpec.groovy`: final class and final method AOP diagnostics are
  cataloged as unsupported.
* `IntroductionAdviceWithNewInterfaceSpec.groovy`: non-generic additional
  interface introduction is covered for concrete, abstract, and protocol-style
  Python beans; generic `ApplicationEventListener` interface introduction is
  pending as `PY-INJECT-0068`.
* `OriginatingElementsSpec.groovy`: Java static originating-element registry
  assertions are cataloged as unsupported compiler infrastructure behavior.
* `GeneratedAnnotationSpec.groovy`: Java generated-class bytecode annotation
  counting is cataloged as unsupported compiler-output behavior.
* `InjectFieldAbstractIntroductionSpec.groovy`: injected members and executable
  methods on an introduced abstract class are covered, using Python property
  setter injection methods for attribute-backed injection points.
* `MethodAdapterSpec.groovy`: event-listener adapter presence, requirements,
  inherited class metadata, event type arguments, multiple lifecycle methods,
  and async listener behavior are covered through `PythonEventListenerSpec`.
* `InterceptedAdapterSpec.groovy`: around advice on event-listener adapter
  methods is pending as `PY-INJECT-0069` because the Python adapter path invokes
  the around interceptor twice for one event.
* `SessionProxySpec.groovy`: Hibernate `Session`/`SessionFactory` JVM proxy
  method emission is cataloged as unsupported Java library proxy behavior.
* `IntroductionInnerInterfaceSpec.groovy`: Java nested interface introduction
  cases are cataloged as unsupported source-model behavior.
* `MyAbstractRepoSpec.groovy`: concrete methods on introduced abstract
  repository-style beans are covered; Java default interface methods remain
  unsupported.
* `AroundConstructCompileSpec.groovy`: constructor interceptor invocation,
  around-construct-only method behavior, constructor-only binding, type plus
  constructor binding, and factory-method construction binding are covered by
  `AroundConstructSpec`. Combined `@Around` plus `@AroundConstruct` behavior is
  pending as `PY-INJECT-0070`; the local Java annotation transformer fixture is
  cataloged as unsupported.
* `NamedAopAdviceSpec.groovy`: named refreshable `@EachProperty` factory beans
  and qualified AOP proxy lookup are covered by `NamedAopAdviceSpec`.
* `ExecutableSuperclassSpec.groovy`: a factory subclass inheriting class-level
  executable methods from a superclass is pending as `PY-INJECT-0071`.
* `IntroducedBeanVisitorSpec.groovy`: visitor-produced annotations on
  introduced methods are pending as `PY-INJECT-0072`; generic introduced method
  signatures from the same source area remain covered by the existing generic
  introduction pending IDs.
* `IntroducedWithRepeatableAnnotationSpec.groovy`: repeatable annotation
  metadata on inherited introduced methods is pending as `PY-INJECT-0073`.
* `MappedIntroductionOnConcreteClassSpec.groovy`: Java annotation-mapped
  introduction on a concrete class is pending as `PY-INJECT-0074`.
* `PropertyAdviceSpec.groovy`: class-level around advice on attribute-backed
  property setters is pending as `PY-INJECT-0075`.
* `IntroductionWithAroundOnConcreteClassSpec.groovy`: the portable combined
  introduction-and-around concrete-class behavior is covered for Java and Groovy
  sources; fixture matrices, exact executable method counts, JavaBean accessor
  names, and JVM multidimensional array properties are cataloged as unsupported.
* `MyIsEnumInTypeArgumentSpec.groovy`: enum metadata on generic method
  parameter type arguments is covered by `EnumElementSpec`.
* `MyRepo3Spec.groovy`: Kotlin coroutine repository introduction and
  second-round generated Kotlin source behavior are cataloged as unsupported
  Kotlin-specific source behavior.
* `CustomVisitorSpec.groovy`: controller class and route-method visitor
  filtering, controller-scoped method visits, and inject method element visits
  are covered by `CustomVisitorSpec`; Java/Groovy field visitor counts and
  field visibility portions are cataloged as unsupported. Python `@Generated`
  class visitor exclusion is pending as `PY-INJECT-0076`.
* `ElementAnnotateSpec.groovy`: type visitor mutation of executable method
  parameter metadata is covered by `AnnotateMethodParameterSpec`; broader
  class, method, return type, property, and metadata writer mutation behavior is
  covered by the existing Python annotate specs.
* `AddStereotypesFromVisitorSpec.groovy`: visitor-added scope and qualifier
  stereotypes on Java annotation fixtures are pending as `PY-INJECT-0077` and
  `PY-INJECT-0078`.
* `AnnotationMetadataSpec.groovy`: visitor-mutated method metadata based on
  method annotation default values is pending as `PY-INJECT-0079`.
* `ImportTypeElementSpec.groovy`: `@ClassImport` generated introspection
  metadata for imported Java classes and interfaces is pending as
  `PY-INJECT-0080`; exact visitor counts for imported Java fields, methods,
  constructors, and enum constants are cataloged as unsupported source-model
  behavior.
* `MixinSpec.groovy`: mixin-applied introspection and copied property, method,
  and argument annotation metadata are pending as `PY-INJECT-0081`.
* `IntroductionVisitorSpec.groovy`: visitor-visible concrete generic type
  substitution on introduced Java-interface methods is pending as
  `PY-INJECT-0082`.
* `BuildElementBuilderAopOnMethodSpec.groovy` and
  `BuildElementBuilderAopOnTypeSpec.groovy`: AOP metadata applied to associated
  beans registered through `BeanElementBuilder` is pending as `PY-INJECT-0083`
  and `PY-INJECT-0084`.
* `AnnotateReplacesSpec.groovy`: visitor-added `@Factory`, `@Bean`, and
  `@Replaces` metadata for replacement factory methods is pending as
  `PY-INJECT-0085`.
* `InheritedConfigurationReaderPrefixSpec.groovy`: supported
  `ConfigurationReader.PREFIX` alias paths with and without `basePrefix` are
  covered by `ConfigurationPropertiesSpec`; the source specs' documented
  unsupported alias-to-`ConfigurationReader.value` cases are cataloged as
  unsupported.
* `ConfigurationMetadataSpec.groovy`: Spring configuration metadata resource
  generation for Python `@ConfigurationProperties` classes is pending as
  `PY-INJECT-0086`.
* `ConfigurationJsonSchemaSpec.groovy`,
  `ConfigurationJsonSchemaDefaultsSpec.groovy`, and
  `ConfigurationJsonSchemaValidationSpec.groovy`: configuration JSON schema
  resource generation is pending as `PY-INJECT-0087`.
* `ConfigurationBuilderSpec.groovy` and `ConfigurationBuilderSpec2.groovy`:
  portable configuration builder binding is covered for Python attributes with
  write prefixes, multiple builder instances with distinct configuration
  prefixes, and inherited Java builder methods; Java private-field/getter
  diagnostics, Java interface builder fixtures, and enum `Set` fixture details
  are cataloged as unsupported.
* `FactoryWithScopedProxySpec.groovy`: non-generic refreshable factory bean
  lazy initialization and qualified refreshable factory beans are covered;
  generic scoped-proxy factory lookup is pending as `PY-INJECT-0088`; JVM
  constructor-mode diagnostics and `MockBean` fixture compilation are cataloged
  as unsupported.
* `EachPropertyParseSpec.groovy`: nested `@ConfigurationProperties` under
  `@EachProperty` computes the expected wildcard `ConfigurationReader` prefix
  and exposes wildcard property injection metadata on the generated injection
  method.
* `BeanWithPostConstructSpec.groovy` and `BeanWithPreDestroySpec.groovy`:
  lifecycle metadata, dependency injection before post-construct, post-construct
  invocation, pre-destroy invocation, and factory `preDestroy` methods are
  covered by `PythonLifecycleSpec` and `FactoryBeanMethodSpec`; Java
  private/protected visibility and Groovy anonymous-class fixture shapes are
  cataloged as unsupported.
* `BeanElementVisitorSpec.groovy`: `BeanElementVisitor` inspection of simple
  bean and factory bean metadata, bean veto, and associated bean registration
  are covered by `BeanElementSpec`.
* `ConfigurationPropertiesBuilderSpec.groovy`: portable configuration builder
  binding through factory methods, `includes`, explicit `value`/
  `configurationPrefix`, and inherited Java builder methods are covered by
  `ConfigurationBuilderSpec`; source-specific Java/Groovy fixture shapes are
  cataloged as unsupported.

The next source area has not been selected yet.

After that, continue with the next uncataloged direct subclass from the
inventory in `DISABLED_TESTS.md`.

## Gradle And GraalPy

The build should resolve `25.1.0-SNAPSHOT` GraalPy artifacts from the included
GraalPy extension checkout. The normal test command should work without a
manual bundle checkout override.

If bundle resolution fails, diagnose the included repository wiring first
instead of relying on a local checkout path.

## Verification Commands

Focused test:

```bash
./gradlew :micronaut-inject-python-test:test --tests <fully.qualified.SpecName>
```

Full verification:

```bash
./gradlew :micronaut-inject-python-test:test
```

## Acceptance Criteria

* Full `:micronaut-inject-python-test:test` passes.
* All pending Python tests are listed in `DISABLED_TESTS.md`.
* All intentionally unsupported source tests are cataloged with reasons.
* Already covered source behavior points to concrete Python coverage.
* No production Python implementation changes are included unless separately
  approved.
