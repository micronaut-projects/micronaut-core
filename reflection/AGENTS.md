# micronaut-reflection

Reflective implementations of the metadata the Micronaut annotation processors generate at compile time, for the
types the processors never saw. Package `io.micronaut.reflection`, everything `@Experimental`, `@since 5.2.0`.

## The idea

The framework describes a type through `AnnotationMetadata`, `Argument`, `ExecutableMethod`, `BeanIntrospection`
and `BeanDefinition`, all generated when the type is compiled. A specification that has to handle any class -
Jakarta Validation, Jakarta REST, CDI - meets types that were not compiled with the processors and has to
describe them through `java.lang.reflect` instead. Before this module every consumer did that itself, each
differently. This module is the one implementation, and it is shaped exactly like the generated metadata so that
code written against generated metadata works unchanged on a type that has none.

## The invariant

**For the same declaration, the metadata built here at runtime and the metadata the processors generate at
compile time must be equal, and must answer every read method identically.** `AnnotationMetadata`, `Argument`,
the bean methods, properties, constructor and injection points of a bean: same annotations, same stereotypes,
same repeatable containers, same defaults, same generic resolution, same order of members.

The point of the invariant is replaceability. A consumer must not be able to tell whether the metadata in its
hands was generated or read reflectively, so a generated description can be swapped for a reflective one (a type
the processors never saw) and a reflective one for a generated one (the type gets compiled with the processors
later) without the consumer changing behaviour. The generated side is the specification: anywhere the two
disagree, the bug is on the reflective side - unless the processors disagree with each other, see below.

Consequences for how the code is written:

- Do not invent behaviour. When unsure what an `Argument` or an `AnnotationMetadata` should answer, compile the
  same declaration with the processors and read what they generate, then make the reflective side answer that.
- The processors are the reference for edge cases too: `? super Book` resolves to `Book`, a type variable inside
  its own bound is the erasure of that bound, a method-position annotation whose target includes `TYPE_USE` is on
  the method *and* on its return value. These are not intuitions; each one was read off generated metadata.
- Reuse core's conversions rather than copying them: `AnnotationValue.of(Annotation)`, `Argument.of`,
  `AnnotationMetadataHierarchy`. A second copy of a conversion drifts.
- Never extend the deprecated `AbstractExecutableMethod`; none of the executables here do.
- Core and `inject` can never depend on this module (it depends on `micronaut-inject`). A core class can only be
  deprecated in favour of something here once core itself has stopped calling it.

## Where things live

| Class | What it is |
| --- | --- |
| `ReflectionAnnotations` | `AnnotationMetadata` / `AnnotationValue` from an `AnnotatedElement` or annotation instances: stereotypes, repeatable containers, registered defaults, `@NonBinding`; `synthesize` builds an annotation back from a value |
| `ReflectionAnnotationCustomizer` | the runtime counterpart of the processors' annotation mappers, as a service |
| `ReflectionArguments` | `Argument` from `AnnotatedType`, `Parameter`, `Field`, `Type`; type variables become placeholders, wildcards their bound, generic arrays their raw array; generic super type resolution; `toType` back to `java.lang.reflect.Type` |
| `ReflectionExecutableMethod`, `ReflectionBeanConstructor`, `IntrospectedExecutableMethod` | executables over a `Method`, a `Constructor`, a `BeanMethod` |
| `ReflectionExecutables` | a `Method` resolved to the best metadata available: bean definition, then introspection, then reflection |
| `ReflectionBeanIntrospection`, `ReflectiveIntrospection`, `SupplementedBeanIntrospection`, `ReflectionBeanIntrospector`, `MethodHierarchy` | an introspection over a class, and what each level of a method hierarchy declares |
| `ReflectionBeanDefinition` | a `BeanDefinition` over a class, registered at runtime as a `RuntimeBeanDefinition`, resolved through `AbstractInitializableBeanDefinition` like a generated bean |
| `ReflectionIntrospectionPolicy`, `ReflectionIntrospectionConfiguration`, `ReflectionBeanIntrospectionFallback` | reflection in the shared introspector, off by default, allowed per pattern through `micronaut.introspection.allow-reflection` |

## How the invariant is tested

Tests are Spock specs under `src/test/groovy`, and most of them are *parity specs*: the same declaration
described both ways, compared read method by read method. Three sources of generated metadata are available:

- **Groovy fixtures** (`src/test/groovy/.../*.groovy` next to the specs) are compiled by the Groovy AST
  transformations of `inject-groovy`, so an `@Introspected` Groovy class has a generated introspection at test
  time: `BeanIntrospector.SHARED.getIntrospection(Fixture)` versus `ReflectionBeanIntrospection.of(Fixture)`.
- **Java compiled in memory** through `AbstractTypeElementSpec` (`inject-java-test`): `buildBeanIntrospection`,
  `buildBeanDefinition` and friends run the real Java processor over a source string, and the resulting class is
  described reflectively from `generated.beanType`. This is the Java processor, the reference.
- **Java fixtures** under `src/test/java` are deliberately *not* processed - they stand for the types the
  processors never saw - and cannot be used for the generated side.

Write a parity case in this order: first assert what the generated side answers today, as a literal
(`tags(returnOf(generated, method)) == ["from-impl"]`), then assert the reflective side equals the generated
side. The literal proves the generated side is not itself broken and makes a change on the generated side visible
here instead of silently followed; the equality is the invariant.

When a case fails, find out which side is wrong before touching either. Compile the declaration with *both*
processors when the annotation is one they could treat differently (type-use annotations, repeatable containers,
inherited members). If the Java processor and the reflective side agree and only the Groovy processor differs,
the gap is in `inject-groovy`, not here: record it as a `@PendingFeature` whose reason names the gap, with the
expectation set to what the Java processor and the class file say, so the pending case turns green when the
Groovy side is fixed. Do not bend the reflective side towards one processor at the cost of the other.

Divergences known and recorded as pending or documented: see `OverriddenReturnParitySpec` (Groovy processor
misses a method-position `TYPE_USE` annotation on the return type), `JavacBeanMetadataParitySpec` (a type-use
member written with its default), and the four `*ParitySpec`s ported from core's own suites
(`AnnotationReflectionUtilsParitySpec`, `GenericTypeUtilsParitySpec`, `ArgumentOfTypeParitySpec`,
`ReflectionUtilsParitySpec`).

## Running

```
./gradlew :micronaut-reflection:test
./gradlew :micronaut-reflection:test --tests '*SomeSpec*'
```

A change to `ReflectionArguments` or `ReflectionAnnotations` is a change to how every consumer reads a class:
run the whole module suite, not only the spec you touched. A change that also touches `core`, `inject`,
`core-processor` or a processor needs `:micronaut-core:test :micronaut-inject:test` and the processor's suite too.
