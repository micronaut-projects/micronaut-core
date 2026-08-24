package io.micronaut.inject.annotation

import io.micronaut.inject.reflection.Customized
import io.micronaut.inject.reflection.Tag
import spock.lang.Specification

class ReflectionAnnotationCustomizerSpec extends Specification {

    void "a registered customizer derives a member of the annotation it supports"() {
        when: "an annotation the customizer supports"
        def metadata = ReflectionAnnotationMetadataBuilder.build(Marked.getDeclaredField("named"))

        then: "the derived member is in the metadata, next to the declared one"
        metadata.stringValue(Customized, "value").get() == "one"
        metadata.stringValue(Customized, "derived").get() == "from-one"
    }

    void "the customizer sees the values as the builder converts them, defaults included"() {
        when: "the member the customizer derives from is left at its default, so it is not a value"
        def metadata = ReflectionAnnotationMetadataBuilder.build(Marked.getDeclaredField("bare"))

        then: "it derives from what it was given, which is nothing"
        metadata.stringValue(Customized, "derived").get() == "from-nothing"
    }

    void "an annotation the customizer does not support is untouched"() {
        when:
        def metadata = ReflectionAnnotationMetadataBuilder.build(Marked.getDeclaredField("tagged"))

        then:
        metadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["plain"]
        metadata.getAnnotationValuesByType(Tag)[0].stringValue("derived").empty
    }

    static class Marked {

        @Customized("one")
        String named

        @Customized
        String bare

        @Tag("plain")
        String tagged
    }
}
