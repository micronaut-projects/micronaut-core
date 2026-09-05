package io.micronaut.reflection

import io.micronaut.reflection.Customized
import io.micronaut.reflection.Tag
import spock.lang.Specification

class ReflectionAnnotationCustomizerSpec extends Specification {

    void "a registered customizer derives a member of the annotation it supports"() {
        when: "an annotation the customizer supports"
        def metadata = ReflectionAnnotations.metadataOf(Marked.getDeclaredField("named"))

        then: "the derived member is in the metadata, next to the declared one"
        metadata.stringValue(Customized, "value").get() == "one"
        metadata.stringValue(Customized, "derived").get() == "from-one"
    }

    void "the customizer sees the values as the builder converts them, defaults included"() {
        when: "the member the customizer derives from is left at its default, so it is not a value"
        def metadata = ReflectionAnnotations.metadataOf(Marked.getDeclaredField("bare"))

        then: "it derives from what it was given, which is nothing"
        metadata.stringValue(Customized, "derived").get() == "from-nothing"
    }

    void "an annotation the customizer does not support is untouched"() {
        when:
        def metadata = ReflectionAnnotations.metadataOf(Marked.getDeclaredField("tagged"))

        then:
        metadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["plain"]
        metadata.getAnnotationValuesByType(Tag)[0].stringValue("derived").empty
    }

    void "a contract the customizer says is retainable is retained, though it carries no marker"() {
        when: "an annotation of a family whose contract is not annotated @Retainable"
        def metadata = ReflectionAnnotations.metadataOf(Marked.getDeclaredField("ranged"))
        def retained = metadata.getAnnotation(Ranged).getStereotypes()

        then: "the contract and the annotation composing it are retained, as they would be were it marked"
        retained*.getAnnotationName() == [Governed.name, Bounded.name]

        and: "and the retained occurrence keeps the contract of its own family in turn"
        retained.find { it.getAnnotationName() == Bounded.name }.getStereotypes()*.getAnnotationName() == [Governed.name]
    }

    void "an override the customizer maps onto @AliasFor is applied to the occurrence it overrides"() {
        when: "the composing annotation sets the member declaring the override"
        def metadata = ReflectionAnnotations.metadataOf(Marked.getDeclaredField("ranged"))

        then: "the composed occurrence carries it, not the value the composing annotation meta-annotates with"
        metadata.getAnnotation(Ranged).getStereotypes()
            .find { it.getAnnotationName() == Bounded.name }.intValue("least").getAsInt() == 7
    }

    void "an override asking for the default is applied where the member is left at its default"() {
        when: "the member is left at its default, which the mapped @AliasFor asks to apply"
        def metadata = ReflectionAnnotations.metadataOf(Marked.getDeclaredField("rangedByDefault"))

        then: "the default of the overriding member wins over what the composed annotation declares"
        metadata.getAnnotation(Ranged).getStereotypes()
            .find { it.getAnnotationName() == Bounded.name }.intValue("least").getAsInt() == 1
    }

    void "a family the customizer says nothing about is not retained"() {
        expect: "@Customized is neither marked nor spoken for, so it composes no tree"
        ReflectionAnnotations.metadataOf(Marked.getDeclaredField("named"))
            .getAnnotation(Customized).getStereotypes() == null
    }

    static class Marked {

        @Ranged(least = 7)
        String ranged

        @Ranged
        String rangedByDefault


        @Customized("one")
        String named

        @Customized
        String bare

        @Tag("plain")
        String tagged
    }
}
