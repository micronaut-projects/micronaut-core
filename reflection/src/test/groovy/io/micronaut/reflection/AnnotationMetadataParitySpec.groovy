package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

/**
 * The metadata the annotation processors generate for an element and the metadata
 * {@link ReflectionAnnotations} builds for the same element, compared: a caller reading a type the processors
 * never saw must read what it would have read had they seen it.
 */
class AnnotationMetadataParitySpec extends Specification {

    private static String render(AnnotationValue<?> value, String indent) {
        StringBuilder out = new StringBuilder()
        out << indent << value.getAnnotationName() << " " << new TreeMap<>(value.getValues().findAll { k, v ->
            k.toString() != '$stereotypes'
        }.collectEntries { k, v -> [(k.toString()): canonical(v)] }) << "\n"
        value.getStereotypes()?.each { out << render(it, indent + "  ") }
        return out.toString()
    }

    private static Object canonical(Object v) {
        // one side may hold a synthesized annotation where the other holds the value it was built from: the
        // shapes are compared, not which of the two forms a member happens to be stored in
        if (v instanceof java.lang.annotation.Annotation) {
            return canonical(AnnotationValue.of(v))
        }
        if (v instanceof AnnotationValue) {
            return v.getAnnotationName() + new TreeMap<>(v.getValues().collectEntries { k, x -> [(k.toString()): canonical(x)] })
        }
        if (v?.getClass()?.isArray()) {
            return (v as Object[]).toList().collect { canonical(it) }
        }
        // one side may hold a member as a list where the other holds an array
        if (v instanceof Collection) {
            return v.collect { canonical(it) }
        }
        return String.valueOf(v)
    }

    private static String describe(AnnotationMetadata metadata) {
        StringBuilder out = new StringBuilder()
        out << "  annotations: " << metadata.getAnnotationNames().toSorted() << "\n"
        out << "  stereotypes: " << metadata.getStereotypeAnnotationNames().toSorted() << "\n"
        for (name in metadata.getAnnotationNames().toSorted()) {
            out << render(metadata.getAnnotation(name), "    ")
        }
        return out.toString()
    }

    void "the metadata of the property #property is built as the processor builds it"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty(property, String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField(property))

        expect: "the annotations, the stereotypes and the retained tree, with its values, are the same"
        describe(reflective) == describe(compileTime)

        where:
        property << ["name", "code", "labelled", "spread"]
    }

    void "a composed annotation retains what it composes, with the members it overrides applied"() {
        given:
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("name"))
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty("name", String).getAnnotationMetadata()

        when: "the tree under the composed annotation"
        def retained = reflective.getAnnotation(Username).getStereotypes()

        then: "the contract it declares and the annotation it composes are retained, the marker itself is not"
        retained*.getAnnotationName() == compileTime.getAnnotation(Username).getStereotypes()*.getAnnotationName()
        retained*.getAnnotationName() == [Contract.name, Sized.name]

        and: "the composed occurrence carries the member the composing annotation overrides through @AliasFor"
        retained.find { it.getAnnotationName() == Sized.name }.intValue("min").getAsInt() == 8

        and: "and keeps its own retainable stereotypes in turn"
        retained.find { it.getAnnotationName() == Sized.name }.getStereotypes()*.getAnnotationName() == [Contract.name]

        and: "while the contract, which composes nothing retainable, keeps no subtree"
        retained.find { it.getAnnotationName() == Contract.name }.getStereotypes() == null
    }

    void "a member aliasing another member of the same annotation sets it, as it does at compilation time"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty("labelled", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("labelled"))

        expect: "writing one of the pair sets the other"
        reflective.stringValue(Labelled, "name").get() == "shortcut"
        reflective.stringValue(Labelled, "value").get() == "shortcut"

        and:
        reflective.stringValue(Labelled, "name") == compileTime.stringValue(Labelled, "name")
    }

    void "an annotation composed more than once is retained once per occurrence, each overridden by its index"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty("spread", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("spread"))
        def sized = { m -> m.getAnnotation(Spread).getStereotypes().findAll { it.getAnnotationName() == Sized.name } }

        expect: "the compiler puts the two occurrences in a container, and both are retained all the same"
        sized(reflective).size() == 2

        and: "each carries the member the alias of its index overrides"
        sized(reflective)*.intValue("min")*.getAsInt() == [7, 9]

        and:
        sized(reflective)*.intValue("min")*.getAsInt() == sized(compileTime)*.intValue("min")*.getAsInt()
    }

    void "an annotation composing nothing retainable carries no tree"() {
        expect:
        ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("code"))
            .getAnnotation(Sized).getStereotypes()*.getAnnotationName() == [Contract.name]

        and: "the marker is never retained as an occurrence of its own"
        !ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("code"))
            .getAnnotation(Sized).getStereotypes()*.getAnnotationName().contains("io.micronaut.core.annotation.Retainable")
    }
}
