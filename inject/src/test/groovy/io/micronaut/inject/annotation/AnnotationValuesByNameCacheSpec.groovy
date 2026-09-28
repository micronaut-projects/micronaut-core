package io.micronaut.inject.annotation

import spock.lang.Specification

class AnnotationValuesByNameCacheSpec extends Specification {

    void "a raw value miss on a non-repeatable annotation does not populate the by-type cache"() {
        given:
        def metadata = new DefaultAnnotationMetadata(
                ["foo.Bar": [value: "a"]] as Map,
                null,
                null,
                ["foo.Bar": [value: "a"]] as Map,
                null
        )

        when:
        def values = metadata.stringValues("foo.Missing", "value")
        def bar = metadata.stringValues("foo.Bar", "value")

        then:
        values.length == 0
        bar == ["a"] as String[]
        !metadata.findAnnotation("foo.Missing").isPresent()
        metadata.findAnnotation("foo.Bar").get().stringValue().get() == "a"
        metadata.getAnnotationValuesByName("foo.Missing").isEmpty()
        metadata.@annotationValuesByType.isEmpty()
    }

    void "the by-name query does not write the by-type cache"() {
        given:
        def metadata = new DefaultAnnotationMetadata(
                ["foo.Bar": [value: "a"]] as Map,
                null,
                null,
                ["foo.Bar": [value: "a"]] as Map,
                null
        )

        when:
        def first = metadata.getAnnotationValuesByName("foo.Bar")

        then:
        first.size() == 1
        first[0].stringValue().get() == "a"
        metadata.@annotationValuesByType.isEmpty()
    }

    void "mutable metadata resolves annotation values by name after a change"() {
        given:
        def metadata = new MutableAnnotationMetadata()

        expect:
        metadata.getAnnotationValuesByName("foo.Bar").isEmpty()
        metadata.stringValues("foo.Bar", "value").length == 0

        when:
        metadata.addDeclaredAnnotation("foo.Bar", [value: "a"] as Map)

        then:
        metadata.getAnnotationValuesByName("foo.Bar").size() == 1
        metadata.getAnnotationValuesByName("foo.Bar")[0].stringValue().get() == "a"
        metadata.stringValues("foo.Bar", "value") == ["a"] as String[]
    }
}
