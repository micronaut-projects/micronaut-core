package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.inject.annotation.AnnotationMetadataException
import io.micronaut.inject.annotation.AnnotationMetadataSupport
import spock.lang.Specification

class SynthesizedAnnotationSpec extends Specification {

    void "an annotation type the shared proxy cannot be built for is synthesized all the same"() {
        given: "a package private annotation type, as a specification nests one in a class of its own"
        def value = new AnnotationValue<Restricted>(Restricted.name, [level: 3])

        when: "the shared path is asked for it"
        AnnotationMetadataSupport.buildAnnotation(Restricted, value)

        then: "it cannot build one"
        thrown(AnnotationMetadataException)

        when:
        Restricted synthesized = ReflectionAnnotations.synthesize(Restricted, value)

        then: "the members are the ones of the value, the ones it does not carry are the defaults of the type"
        synthesized.level() == 3
        synthesized.name() == "unnamed"
        synthesized.annotationType() == Restricted
        synthesized.toString() == value.toString()

        and: "reading it back yields the values it was built from; a fallback instance carries no annotation value of its own"
        !(synthesized instanceof AnnotationValueProvider)
        ReflectionAnnotations.valueOf(synthesized) == value
    }

    void "a synthesized annotation compares as the annotation contract requires"() {
        given:
        def written = RestrictedHolder.getAnnotation(Restricted)
        def synthesized = ReflectionAnnotations.synthesize(Restricted, ReflectionAnnotations.valueOf(written))
        def other = ReflectionAnnotations.synthesize(Restricted, new AnnotationValue<Restricted>(Restricted.name, [level: 9]))

        expect: "equal to the annotation the compiler made, and to itself, by every member"
        synthesized == written
        synthesized.hashCode() == written.hashCode()
        synthesized != other
        !synthesized.equals(null)
        !synthesized.equals("not an annotation")
    }

    void "an annotation type the shared proxy can be built for still comes from it"() {
        given:
        def value = new AnnotationValue<Tag>(Tag.name, [value: "shared"])

        expect:
        ReflectionAnnotations.synthesize(Tag, value).value() == "shared"
        ReflectionAnnotations.synthesize(Tag, value).annotationType() == Tag
    }
}
