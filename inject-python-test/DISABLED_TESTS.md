# Python Inject Disabled Test Catalog

This catalog tracks Python coverage decisions for direct subclasses of:

* `AbstractBeanDefinitionSpec`
* `AbstractTypeElementSpec`
* `AbstractKotlinCompilerSpec`

The first pass keeps changes in `micronaut-inject-python-test` only. Production Python implementation changes are intentionally out of scope.

## Inventory Commands

```bash
rg -n "extends\\s+(AbstractBeanDefinitionSpec|AbstractTypeElementSpec|AbstractKotlinCompilerSpec)" \
  inject-groovy/src/test/groovy inject-groovy-test/src/main/groovy \
  inject-java/src/test/groovy inject-java-test/src/test/groovy inject-java-test/src/main/groovy \
  inject-kotlin/src/test/groovy inject-kotlin-test/src/main/groovy
```

Feature counts were calculated from the same direct-subclass set by counting Spock feature methods declared directly in each spec.

## Direct Subclass Counts

| Source root | Direct subclass count |
| --- | ---: |
| `inject-groovy/src/test/groovy` | 86 |
| `inject-groovy-test/src/main/groovy` | 1 |
| `inject-java/src/test/groovy` | 184 |
| `inject-java-test/src/test/groovy` | 10 |
| `inject-java-test/src/main/groovy` | 1 |
| `inject-kotlin/src/test/groovy` | 19 |
| `inject-kotlin-test/src/main/groovy` | 0 |
| **Total** | **301** |

| Base spec | Direct subclass count |
| --- | ---: |
| `AbstractBeanDefinitionSpec` | 87 |
| `AbstractTypeElementSpec` | 195 |
| `AbstractKotlinCompilerSpec` | 19 |
| **Total declared Spock features in direct subclasses** | **5052** |

## Migration Rules

Portable tests assert Micronaut behavior independent of Java, Groovy, or Kotlin syntax: annotation metadata, bean definitions, introspection, class elements, injection, qualifiers, factories, AOP metadata, generics, and visitor-visible structure.

Unsupported tests assert source-language behavior rather than Micronaut behavior: Groovy AST quirks, Groovy closures/traits, Groovy-specific property reconstruction, Java package-private/default-method/package-info/record/visibility rules, Kotlin data/companion/KSP behavior, and expression-language specs inherited through intermediate expression specs.

If Python already has equivalent coverage, the source feature is cataloged as covered instead of duplicated. If the behavior is portable but Python cannot currently express or report the same model, a Python test is added with `@PendingFeature` and tracked below.

## Pending Python Tests

| ID | Source language | Source spec | Source feature | Python spec | Subsystem | Reason | Focused test command | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PY-INJECT-0001 | Java | `inject-java/src/test/groovy/io/micronaut/inject/beans/BeanDefinitionSpec.groovy` | `test declared generics from definition` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/BeanDefinitionSpec.groovy` | Bean definition generics | Python `Generic[K, V]` currently reports `GenericService` for `definition.getGenericBeanType().getTypeString(true)` instead of `GenericService<Object, Object>`. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.BeanDefinitionSpec` | `@PendingFeature` |
| PY-INJECT-0002 | Java | `inject-java/src/test/groovy/io/micronaut/inject/beans/BeanDefinitionSpec.groovy` | `test fail compilation on invalid exposed bean type` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/BeanDefinitionSpec.groovy` | Bean definition typed exposure validation | Python emits `Bean defines an exposed type [...]` as a compiler diagnostic, but the current test harness does not surface it as a thrown compilation failure from `buildBeanDefinition`. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.BeanDefinitionSpec` | `@PendingFeature` |
| PY-INJECT-0003 | Java | `inject-java/src/test/groovy/io/micronaut/inject/beans/BeanDefinitionSpec.groovy` | `test declared generics from reference` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/BeanDefinitionSpec.groovy` | Bean definition reference generics | Same root cause as `PY-INJECT-0001`: Python generic bean references do not yet render unresolved placeholders as `Object` type arguments. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.BeanDefinitionSpec` | `@PendingFeature` |
| PY-INJECT-0004 | Java | `inject-java/src/test/groovy/io/micronaut/inject/scope/DefaultScopeSpec.groovy` | `test default scope no override` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/DefaultScopeSpec.groovy` | Default scope stereotypes | A Python decorator meta-annotated with `@DefaultScope(Singleton)` does not currently produce a bean definition unless an explicit scope is also present. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.DefaultScopeSpec` | `@PendingFeature` |
| PY-INJECT-0005 | Java | `inject-java/src/test/groovy/io/micronaut/inject/annotation/AnnotationDefaultValuesSpec.groovy` | `test getRequiredValue from AnnotationValue` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/annotate/AnnotationMetadataWriterSpec.groovy` | Annotation default values | A Python annotation decorator applied directly to a class is not currently visible from `BeanDefinition.getAnnotationValuesByName("python.Topic")` with default member values. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.annotate.AnnotationMetadataWriterSpec` | `@PendingFeature` |
| PY-INJECT-0006 | Java | `inject-java/src/test/groovy/io/micronaut/inject/annotation/AnnotationDefaultValuesSpec.groovy` | `test write annotation default values for constructor arguments` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/annotate/AnnotationMetadataWriterSpec.groovy` | Constructor parameter annotation metadata | A Python annotation decorator used as `Annotated[Type, ParamAnn]` does not currently appear in constructor argument annotation metadata without an additional recognized stereotype. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.annotate.AnnotationMetadataWriterSpec` | `@PendingFeature` |
| PY-INJECT-0007 | Java | `inject-java/src/test/groovy/io/micronaut/inject/provider/BeanProviderSpec.groovy` | `test Jakarta Provider is triggering containsBean` | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/inject/BeanProviderSpec.groovy` | Provider injection cache behavior | Python `jakarta.inject.Provider` injection resolves the provider target, but it does not add the extra `containsBean` cache entry during injection that the Java source spec asserts. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.inject.BeanProviderSpec` | `@PendingFeature` |
| PY-INJECT-0008 | Java | `inject-java/src/test/groovy/io/micronaut/inject/optional/OptionalPropertySpec.groovy` | `test get bean with optionals not present` field optional assertions | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/OptionalPropertySpec.groovy` | Optional property injection | Absent Python field properties typed as `OptionalInt`, `OptionalLong`, or `OptionalDouble` are currently injected as `None` instead of the Java optional primitive empty instances. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.OptionalPropertySpec` | `@PendingFeature` |
| PY-INJECT-0009 | Java | `inject-java/src/test/groovy/io/micronaut/inject/optional/OptionalPropertySpec.groovy` | `test get bean with optionals present` field optional assertions | `inject-python-test/src/test/groovy/io/micronaut/python/annotation/processing/test/OptionalPropertySpec.groovy` | Optional property injection | Python field properties typed as `Optional[str]`, `OptionalInt`, `OptionalLong`, or `OptionalDouble` are currently left as `None` instead of being injected from configured property values. | `./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.OptionalPropertySpec` | `@PendingFeature` |

## Intentionally Unsupported Source Tests

| Source language | Source spec/feature | Reason |
| --- | --- | --- |
| Groovy | `inject-groovy/src/test/groovy/io/micronaut/ast/groovy/**` | These assert Groovy AST element behavior, Groovy bean-property conventions, Groovy documentation extraction, enum handling, or reconstruction details. |
| Groovy | `inject-groovy/src/test/groovy/io/micronaut/expressions/**` and `inject-groovy-test/src/main/groovy/**/AbstractEvaluatedExpressionsSpec.groovy` | Expression-language inheritance and Groovy expression fixtures are outside this direct Python inject hardening pass. |
| Groovy | Source features using closures, traits, Groovy config builders, dynamic Groovy properties, or Groovy-only syntax | The purpose is Groovy language behavior rather than language-neutral Micronaut metadata. |
| Java | Features for `record`, `package-info`, package-private visibility, private constructors/methods as Java access-control cases, default interface methods, sealed Java APIs, or no-package compilation | Python has no direct source-language equivalent for these Java compiler and access-rule assertions. |
| Java | Features whose expected diagnostics are javac symbol-resolution messages or Java source parser errors | Python parser/compiler diagnostics are intentionally different. |
| Java | Service-loader and processor-order tests whose assertions are about Java annotation-processing infrastructure rather than visitor-visible Python elements | Python processor integration has separate coverage through existing Python visitor/resource tests. |
| Java/Groovy | `inject-*/src/test/groovy/io/micronaut/inject/executable/inheritance/InheritedExecutableSpec.groovy` features relying on source-level method overloads or Java interface override inheritance | Python does not have Java/Groovy method overloading and has a different interface inheritance model. |
| Kotlin | `inject-kotlin/src/test/groovy/io/micronaut/kotlin/processing/**` features for KSP, Kotlin data classes, companions, suspend functions, Kotlin visibility, Kotlin default values, or Kotlin reconstruction | These validate Kotlin/KSP language mapping and do not have Python syntax equivalents. |

## Already Covered Equivalents

| Source spec/feature | Existing or added Python coverage |
| --- | --- |
| `inject-java-test/src/test/groovy/io/micronaut/inject/visitor/beans/BuildClassElementSpec.groovy` / `test build class element` | `PythonClassElementSpec.test class element exposes generated Python name` |
| `inject-java/src/test/groovy/io/micronaut/inject/beans/BeanDefinitionSpec.groovy` / `test getTypeString for format #format` | `BeanDefinitionSpec.test getTypeString for format #format` |
| Java/Groovy/Kotlin bean definition exposed-type tests using `@Bean(typed=...)` | `BeanDefinitionSpec.test limit the exposed bean types` and `BeanDefinitionSpec.test limit the exposed bean types from reference` |
| `inject-java/src/test/groovy/io/micronaut/inject/beans/BeanDefinitionSpec.groovy` / `test executable method on startup` | `ExecutableMethodSpec.test executable methods can require startup processing` |
| `inject-java/src/test/groovy/io/micronaut/inject/executable/ExecutableSpec.groovy` / `test executable compile spec` | `ExecutableMethodSpec.test class-level @Executable produces executable methods` |
| `inject-java/src/test/groovy/io/micronaut/inject/qualifiers/annotationmember/NonBindingQualifierSpec.groovy` / non-binding qualifier behavior and metadata | `ConstructorInjectionSpec.test constructor injection with non binding annotation member qualifier` and `BeanDefinitionSpec.test non binding qualifier member metadata` |
| `inject-java/src/test/groovy/io/micronaut/inject/requires/RequiresSpec.groovy` / property, environment, bean, missing-bean, class, and missing-class conditions | `RequiresSpec` |
| `inject-java/src/test/groovy/io/micronaut/inject/qualifiers/replaces/ReplacesSpec.groovy` / simple and named bean replacement | `ReplacesSpec` |
| `inject-java/src/test/groovy/io/micronaut/inject/factory/named/ImplicitNamedSpec.groovy` / implicit `@Named` metadata on types and stereotypes | `BeanDefinitionSpec.test implicit named qualifier on type` and `BeanDefinitionSpec.test implicit named qualifier on type via stereotype` |
| `inject-java/src/test/groovy/io/micronaut/inject/scope/DefaultScopeSpec.groovy` / explicit scope override on a default-scope stereotype | `DefaultScopeSpec.test default scope with override` |
| `inject-java/src/test/groovy/io/micronaut/inject/annotation/AnnotationInheritanceSpec.groovy` / declared and inherited type-level scopes, qualifiers, and requirements | `annotate/AnnotationInheritanceSpec` |
| Java/Groovy/Kotlin annotation modify specs for class, method, return type, parameter, property, and annotation metadata writer behavior | `annotate/AnnotateClassSpec`, `annotate/AnnotateMethodSpec`, `annotate/AnnotateMethodReturnSpec`, `annotate/AnnotateMethodParameterSpec`, `annotate/AnnotatePropertySpec`, and `annotate/AnnotationMetadataWriterSpec` |
| Java/Groovy/Kotlin class-element generic type argument tests that assert visitor-visible generic substitutions | `PythonClassElementSpec` generic inheritance, Java-interface, override-method, return-type, and argument-type tests |
| Java/Groovy/Kotlin executable method tests for `@Executable` and executable metadata | `ExecutableMethodSpec` |
| `inject-java/src/test/groovy/io/micronaut/inject/executable/inheritance/InheritedExecutableSpec.groovy` and `inject-groovy/src/test/groovy/io/micronaut/inject/executable/inheritance/InheritedExecutableSpec.groovy` / abstract base is not a bean and inherited executable generics resolve on concrete beans | `ExecutableMethodSpec.test abstract base with executable method is not a bean` and `ExecutableMethodSpec.test inherited executable methods resolve generic arguments` |
| Java/Groovy/Kotlin factory bean method tests for factory method scope and lifecycle behavior | `inject/factory/beanmethod/FactoryBeanMethodSpec` |
| Java/Groovy/Kotlin constructor, method, field, provider, collection, and qualifier injection tests | `inject/ConstructorInjectionSpec` including both `BeanProvider` and `jakarta.inject.Provider`, `inject/MethodInjectionSpec`, and `inject/FieldInjectionSpec` |
| `inject-java/src/test/groovy/io/micronaut/inject/qualifiers/named/NamedQualifierSpec.groovy` / named qualifier injection | `inject/ConstructorInjectionSpec.test constructor injection with named qualifiers`, `inject/MethodInjectionSpec.test method injection with named qualifiers`, and `inject/FieldInjectionSpec.test field injection with named qualifiers` |
| `inject-java/src/test/groovy/io/micronaut/inject/qualifiers/annotation/AnnotationQualifierSpec.groovy` / annotation qualifier injection | `inject/ConstructorInjectionSpec.test constructor injection with annotation qualifiers` |
| `inject-java/src/test/groovy/io/micronaut/inject/qualifiers/repeatable/RepeatableQualifierSpec.groovy` / repeatable qualifier injection and declared qualifier lookup | `inject/RepeatableQualifierSpec` |
| `inject-java/src/test/groovy/io/micronaut/inject/provider/BeanProviderSpec.groovy` / provider bean type references, provider generic metadata, required components, missing providers, injection-point qualifier lookup, and deferred `BeanProvider.containsBean` behavior | `inject/BeanProviderSpec` and `inject/ConstructorInjectionSpec` |
| `inject-java/src/test/groovy/io/micronaut/inject/provider/DisableErrorOnMissingBeanProviderSpec.groovy` / empty-provider builder behavior | `inject/BeanProviderSpec.test missing jakarta provider does not fail when empty providers are allowed` and `inject/BeanProviderSpec.test missing jakarta provider fails when empty providers are disabled` |
| `inject-groovy/src/test/groovy/io/micronaut/inject/value/ValueParseSpec.groovy` / `@Value` metadata on injected field | `ValueSpec.test value annotation metadata on field injection` |
| `inject-java/src/test/groovy/io/micronaut/inject/value/factorywithvalue/FactoryWithValueSpec.groovy` / `@Value` injection on factory method parameters | `ValueSpec.test configuration injection with value on factory method` |
| `inject-java/src/test/groovy/io/micronaut/inject/optional/OptionalPropertySpec.groovy` / constructor optional property injection | `OptionalPropertySpec.test get bean with constructor optionals not present` and `OptionalPropertySpec.test get bean with constructor optionals present` |
| Java/Groovy/Kotlin lifecycle tests for post-construct/pre-destroy behavior | `inject/PythonLifecycleSpec` |
| Java/Groovy/Kotlin bean introspection tests for dataclass properties, constructor instantiation, nested object equality, generated proxy property exclusion, and serde stereotypes | `BeanIntrospectionSpec` |
| Java/Groovy/Kotlin AOP/interceptor metadata tests that assert around/introduction bindings | `AroundAdviceSpec`, `IntroductionAdviceSpec`, `IntroductionGenericInterfaceSpec`, and `MapperIntroductionSpec` |
| Visitor resource-writing tests such as `visitMetaInfFile` behavior | `VisitMetaInfSpec` |
