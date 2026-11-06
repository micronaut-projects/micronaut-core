package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.inject.annotation.AnnotationMetadataException
import io.micronaut.inject.annotation.AnnotationMetadataSupport
import spock.lang.Specification

class SynthesizedAnnotationSpec extends Specification {

    void "a package private annotation type is synthesized by the shared proxy"() {
        given: "a package private annotation type, as a specification nests one in a class of its own"
        def value = new AnnotationValue<Restricted>(Restricted.name, [level: 3])

        when: "the shared path is asked for it"
        Restricted built = AnnotationMetadataSupport.buildAnnotation(Restricted, value)

        then: "it builds one: the JDK defines the proxy of a type that is not public in that type's own package"
        built.level() == 3
        built.name() == "unnamed"
        built.annotationType() == Restricted

        when:
        Restricted synthesized = ReflectionAnnotations.synthesize(Restricted, value)

        then: "the members are the ones of the value, the ones it does not carry are the defaults of the type"
        synthesized.level() == 3
        synthesized.name() == "unnamed"
        synthesized.annotationType() == Restricted

        and: "reading it back yields the values it was built from, off the annotation value the instance carries"
        synthesized instanceof AnnotationValueProvider
        ReflectionAnnotations.valueOf(synthesized) == value
    }

    void "an annotation type the shared proxy cannot be built for is synthesized all the same"() {
        given: "a copy of an annotation type defined by a loader that does not see Micronaut, so that the proxy\
 of the type and AnnotationValueProvider cannot be defined"
        def isolated = new OwnCopy().define(Isolated.name)
        def value = new AnnotationValue(Isolated.name, [level: 3])

        when: "the shared path is asked for it"
        AnnotationMetadataSupport.buildAnnotation(isolated, value)

        then: "it cannot build one"
        thrown(AnnotationMetadataException)

        when:
        def synthesized = ReflectionAnnotations.synthesize(isolated, value)

        then: "the members are the ones of the value, the ones it does not carry are the defaults of the type"
        synthesized.level() == 3
        synthesized.name() == "unnamed"
        synthesized.annotationType() == isolated
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

    void "annotations synthesized from implicit and explicit defaults obey the annotation equality contract"() {
        given:
        Every written = EveryKindBean.getDeclaredField("defaulted").getAnnotation(Every)
        Every implicitDefaults = ReflectionAnnotations.synthesize(Every, ReflectionAnnotations.valueOf(written))
        Every explicitDefaults = ReflectionAnnotations.synthesize(Every, AnnotationValue.of(written))

        expect:
        verifyAll {
            implicitDefaults.equals(explicitDefaults)
            explicitDefaults.equals(implicitDefaults)
            implicitDefaults.equals(written)
            written.equals(implicitDefaults)
            implicitDefaults.hashCode() == explicitDefaults.hashCode()
            new HashSet<>([implicitDefaults, explicitDefaults]).size() == 1
        }
    }

    /**
     * A loader defining the classes it is asked for itself, under the platform loader, so that nothing of
     * Micronaut is visible to the types it defines.
     */
    static class OwnCopy extends ClassLoader {

        OwnCopy() {
            super(ClassLoader.platformClassLoader)
        }

        Class<?> define(String name) {
            def bytes = SynthesizedAnnotationSpec.classLoader
                    .getResourceAsStream(name.replace('.' as char, '/' as char) + ".class").bytes
            defineClass(name, bytes, 0, bytes.length)
        }
    }
}
