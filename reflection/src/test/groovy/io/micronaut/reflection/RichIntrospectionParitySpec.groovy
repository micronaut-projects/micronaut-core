package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.type.Argument
import spock.lang.Specification

/**
 * The description a generated introspection carries and the one {@link ReflectionBeanIntrospection} builds for
 * the same type, compared member by member: a caller switching from one to the other reads the same annotations,
 * with the same values, on the same properties, of the same types.
 *
 * <p>{@link IntrospectionParitySpec} compares the plain shapes; this one compares the ones that carry detail -
 * annotations with members of every kind, an annotation written repeatedly, a stereotype, generic and array and
 * enum property types, and members inherited from a generic super class.</p>
 */
class RichIntrospectionParitySpec extends Specification {

    BeanIntrospection<RichParityBean> generated = BeanIntrospector.SHARED.getIntrospection(RichParityBean)
    BeanIntrospection<RichParityBean> reflective = ReflectionBeanIntrospection.of(RichParityBean)

    BeanIntrospection<IndexedParityBean> generatedIndexed = BeanIntrospector.SHARED.getIntrospection(IndexedParityBean)
    BeanIntrospection<IndexedParityBean> reflectiveIndexed = ReflectionBeanIntrospection.of(IndexedParityBean)

    /**
     * The type of an argument with its type arguments, so that a generic type is compared in full rather than
     * by its erasure.
     */
    private static String describe(Argument<?> argument) {
        return argument.getType().getSimpleName() + (argument.getTypeParameters().length == 0 ? "" :
            "<" + argument.getTypeParameters().collect { describe(it) }.join(", ") + ">")
    }

    private static String describe(BeanMethod<?, ?> method) {
        return method.getName() +
            "(" + method.getArguments().collect { "${it.name}:${describe(it)}:${annotations(it.getAnnotationMetadata())}" }.join(", ") + ")" +
            ":" + describe(method.getReturnType().asArgument()) + " " + annotations(method.getAnnotationMetadata())
    }

    /**
     * An annotation value rendered with its members sorted, so that two metadata carrying the same annotation
     * compare equal whichever order they record its members in.
     */
    private static String render(AnnotationValue<?> value) {
        return new TreeMap<>(value.getValues().collectEntries { member, held -> [(member.toString()): canonical(held)] }).toString()
    }

    private static Object canonical(Object held) {
        if (held instanceof AnnotationValue) {
            return held.getAnnotationName() + render(held)
        }
        if (held?.getClass()?.isArray()) {
            return (held as Object[]).toList().collect { canonical(it) }
        }
        if (held instanceof Collection) {
            return held.collect { canonical(it) }
        }
        return String.valueOf(held)
    }

    private static String annotations(AnnotationMetadata metadata) {
        return "all=" + new TreeMap<>(metadata.getAnnotationNames().collectEntries { [(it): render(metadata.getAnnotation(it))] }) +
            " declared=" + metadata.getDeclaredAnnotationNames().toSorted() +
            " stereotypes=" + metadata.getStereotypeAnnotationNames().toSorted()
    }

    /**
     * The properties of a reflective description without the one the Groovy compiler adds to every class, which
     * a generated description leaves out.
     */
    private static List<String> names(Collection<?> properties) {
        return properties*.name.findAll { it != "metaClass" }.toSorted()
    }

    void "the annotations of the type are the same, with their values and their stereotypes"() {
        expect:
        annotations(reflective.getAnnotationMetadata()) == annotations(generated.getAnnotationMetadata())
    }

    void "the properties are the same, including the ones inherited from the generic super class"() {
        expect:
        names(reflective.getBeanProperties()) == names(generated.getBeanProperties())

        and: "the read and the write ones are the same too"
        names(reflective.getBeanReadProperties()) == names(generated.getBeanReadProperties())
        names(reflective.getBeanWriteProperties()) == names(generated.getBeanWriteProperties())
    }

    void "the type of the property #name is the same, with its type arguments"() {
        expect:
        describe(reflective.getRequiredProperty(name, Object).asArgument()) ==
            describe(generated.getRequiredProperty(name, Object).asArgument())

        where:
        name << ["bounded", "colour", "flag", "generic", "identifier", "nested", "note", "optional", "primitives", "repeated", "strings"]
    }

    void "the annotations of the property #name are the same, with their values and their stereotypes"() {
        expect:
        annotations(reflective.getRequiredProperty(name, Object).getAnnotationMetadata()) ==
            annotations(generated.getRequiredProperty(name, Object).getAnnotationMetadata())

        where:
        name << ["bounded", "colour", "flag", "generic", "identifier", "nested", "note", "optional", "primitives", "repeated", "strings"]
    }

    void "an annotation written repeatedly is read back the same, each occurrence with the members it sets"() {
        given:
        def read = { BeanIntrospection<?> introspection ->
            introspection.getRequiredProperty("repeated", String).getAnnotationMetadata()
                .getAnnotationValuesByType(Tag)
                .collect { [it.stringValue().get(), (it.intValue("priority").isPresent() ? it.intValue("priority").getAsInt() : null), it.enumValue("level", Level).orElse(null)] }
        }

        expect:
        read(reflective) == read(generated)

        and: "which is the two occurrences, each with the members it sets and no other"
        read(generated) == [["one", 3, null], ["two", null, Level.HIGH]]
    }

    void "an executable method is the same, in its arguments, its generic return type and its annotations"() {
        expect:
        reflective.getBeanMethods().findAll { it.name == "combine" }.collect { describe(it) } ==
            generated.getBeanMethods().findAll { it.name == "combine" }.collect { describe(it) }
    }

    void "an executable method reads back the same occurrences of a repeated annotation, the type's composed ones included"() {
        given: "the type carries @Composed, which composes two @Tag, and one @Tag of its own; the method carries a third"
        def read = { BeanIntrospection<?> introspection ->
            introspection.getBeanMethods().find { it.name == "combine" }.getAnnotationMetadata()
                .getAnnotationValuesByType(Tag)*.stringValue()*.orElse(null).toSorted()
        }

        expect:
        read(reflective) == read(generated)

        and: "which is the one the method declares, the one the type declares and the two the type composes"
        read(generated) == ["first", "method", "second", "type"]
    }

    void "the constructor arguments are the same"() {
        expect:
        reflective.getConstructorArguments().collect { "${it.name}:${describe(it)}:${annotations(it.getAnnotationMetadata())}" } ==
            generated.getConstructorArguments().collect { "${it.name}:${describe(it)}:${annotations(it.getAnnotationMetadata())}" }
    }

    void "an annotation the type does not index finds no property, as it finds none in a generated description"() {
        expect: "RichParityBean asks for no index, and carries @Hidden on a property"
        reflective.getIndexedProperties(Hidden).isEmpty()
        generated.getIndexedProperties(Hidden).isEmpty()

        and:
        !reflective.getIndexedProperty(Hidden, "bean").isPresent()
        !generated.getIndexedProperty(Hidden, "bean").isPresent()
    }

    void "an annotation the type indexes finds the same properties, and the same one by value"() {
        expect:
        names(reflectiveIndexed.getIndexedProperties(Hidden)) == names(generatedIndexed.getIndexedProperties(Hidden))
        names(generatedIndexed.getIndexedProperties(Hidden)) == ["three"]

        and:
        reflectiveIndexed.getIndexedProperty(Hidden, "kept").map { it.name } ==
            generatedIndexed.getIndexedProperty(Hidden, "kept").map { it.name }
        generatedIndexed.getIndexedProperty(Hidden, "kept").map { it.name }.orElse(null) == "three"

        and: "and a value no property carries finds none on either side"
        !reflectiveIndexed.getIndexedProperty(Hidden, "absent").isPresent()
        !generatedIndexed.getIndexedProperty(Hidden, "absent").isPresent()
    }

    void "a repeatable annotation the type asks to index holds no property, as it holds none in a generated description"() {
        expect: "the properties do carry the annotation, on either side"
        reflectiveIndexed.getRequiredProperty("one", String).getAnnotationMetadata().hasStereotype(Tag)
        generatedIndexed.getRequiredProperty("one", String).getAnnotationMetadata().hasStereotype(Tag)

        and: "and the index holds neither of them: the processor reads it off the element, which carries a repeatable annotation under its container alone"
        generatedIndexed.getIndexedProperties(Tag).isEmpty()
        reflectiveIndexed.getIndexedProperties(Tag).isEmpty()

        and:
        !generatedIndexed.getIndexedProperty(Tag, "first").isPresent()
        !reflectiveIndexed.getIndexedProperty(Tag, "first").isPresent()
    }
}
