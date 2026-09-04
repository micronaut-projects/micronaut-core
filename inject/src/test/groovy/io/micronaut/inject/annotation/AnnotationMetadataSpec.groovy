package io.micronaut.inject.annotation

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.core.annotation.TypeHint
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.lang.annotation.RetentionPolicy

class AnnotationMetadataSpec extends Specification {

    void "test class values with string"() {
        given:
        AnnotationMetadata metadata = newMetadata(AnnotationValue.builder("foo.Bar").values(AnnotationMetadataSpec, Specification))

        expect:
        metadata.classValues("foo.Bar") == [AnnotationMetadataSpec, Specification] as Class[]
    }

    void "test class values with type"() {
        given:
        AnnotationMetadata metadata = newMetadata(AnnotationValue.builder(EachBean).values(AnnotationMetadataSpec, Specification))

        expect:
        metadata.classValues(EachBean) == [AnnotationMetadataSpec, Specification] as Class[]
    }

    void "test string values with type"() {
        given:
        AnnotationMetadata metadata = newMetadata(AnnotationValue.builder(TypeHint).values(UUID[], UUID))

        expect:
        metadata.stringValues(TypeHint).size() == 2
    }

    void "test empty values then append"() {
        given:
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata([:], null, null, [:], null, false)
        metadata.addAnnotation("foo.Bar", [:])

        when:
        metadata.addRepeatable("foo.Bar", new AnnotationValue("foo.Bar"), RetentionPolicy.RUNTIME)

        then:
        noExceptionThrown()
    }

    void "test null string array entries do not fail property expression check"() {
        given:
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata()

        when:
        metadata.addAnnotation("foo.Bar", [value: ["", null] as String[]])

        then:
        noExceptionThrown()
        !metadata.hasPropertyExpressions()
    }

    void "test synthesized annotation strips internal values and compares with regular annotation instance"() {
        given:
        def annotationValue = new AnnotationValue(
            BindingExample.name,
            [
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["ignored"] as String[],
                value: "same",
                ignored: "one"
            ]
        )

        when:
        BindingExample synthesized = AnnotationMetadataSupport.buildAnnotation(BindingExample, annotationValue)
        BindingExample regular = new BindingExample() {
            @Override
            String value() {
                return "same"
            }

            @Override
            String ignored() {
                return "one"
            }

            @Override
            Class<? extends Annotation> annotationType() {
                return BindingExample
            }
        }

        then:
        synthesized.value() == "same"
        synthesized.ignored() == "one"
        synthesized == regular
        !(synthesized as AnnotationValueProvider).annotationValue().contains(AnnotationUtil.NON_BINDING_ATTRIBUTE)
    }

    void "test buildAnnotation works for bootstrap-loaded JDK annotations"() {
        // Regression test for https://github.com/micronaut-projects/micronaut-jaxrs/issues/640
        // java.lang.Deprecated is loaded by the bootstrap classloader (getClassLoader() == null).
        // The proxy must implement both the annotation type and AnnotationValueProvider, so the
        // chosen classloader has to be able to see AnnotationValueProvider.
        when:
        Deprecated deprecated = AnnotationMetadataSupport.buildAnnotation(Deprecated, null)

        then:
        noExceptionThrown()
        deprecated != null
        deprecated.annotationType() == Deprecated
    }

    void "test getAnnotationValuesByName resolves a repeatable annotation stored under its own name"() {
        given:
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(Requires.name, [property: "foo"])

        when: "the repeatable annotation is not wrapped in its container"
        List<AnnotationValue<Requires>> values = metadata.getAnnotationValuesByName(Requires.name)

        then: "it is resolved by its own name, consistently with the type based variant"
        values.size() == 1
        values[0].stringValue("property").get() == "foo"
        metadata.getAnnotationValuesByType(Requires).size() == 1
    }

    void "test the occurrences of a repeatable annotation are read out one value per occurrence"() {
        given: "a repeatable annotation added twice, which a metadata built at runtime accumulates in a collection"
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredRepeatable("foo.Tags", AnnotationValue.builder("foo.Tag").value("one").build(), RetentionPolicy.RUNTIME)
        metadata.addDeclaredRepeatable("foo.Tags", AnnotationValue.builder("foo.Tag").value("two").build(), RetentionPolicy.RUNTIME)

        expect: "the member holds both occurrences"
        metadata.getAnnotationValuesByName("foo.Tag").size() == 2

        and: "and the accessors that map over it answer one value per occurrence, not one holding the whole collection"
        metadata.stringValues("foo.Tags", AnnotationMetadata.VALUE_MEMBER).length == 2
        metadata.stringValues("foo.Tags", AnnotationMetadata.VALUE_MEMBER) ==
            ['@foo.Tag(value=one)', '@foo.Tag(value=two)'] as String[]
    }

    AnnotationMetadata newMetadata(AnnotationValueBuilder... builders) {

        def values = builders.collect({ it.build() })

        Map<String, Map<CharSequence, Object>> annotations = [:]
        for (AnnotationValue av in values) {
            annotations.put(av.annotationName, av.values)
        }

        return new MutableAnnotationMetadata(
                annotations, null, null, annotations, null, false
        )
    }
}

@interface BindingExample {
    String value() default ""
    String ignored() default ""
}
