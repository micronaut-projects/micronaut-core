package io.micronaut.inject.annotation

import spock.lang.Specification

class AnnotationValuesByNameCacheSpec extends Specification {

    void "annotation values by name are resolved once"() {
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
        def second = metadata.getAnnotationValuesByName("foo.Bar")

        then:
        first.size() == 1
        first[0].stringValue().get() == "a"
        second.is(first)
        metadata.getAnnotationValuesByName("foo.Missing").isEmpty()
        metadata.getAnnotationValuesByName("foo.Missing").is(metadata.getAnnotationValuesByName("foo.Missing"))
    }

    void "mutable metadata resolves annotation values by name after a change"() {
        given:
        def metadata = new MutableAnnotationMetadata()

        expect:
        metadata.getAnnotationValuesByName("foo.Bar").isEmpty()

        when:
        metadata.addDeclaredAnnotation("foo.Bar", [value: "a"] as Map)

        then:
        metadata.getAnnotationValuesByName("foo.Bar").size() == 1
        metadata.getAnnotationValuesByName("foo.Bar")[0].stringValue().get() == "a"
    }
}
