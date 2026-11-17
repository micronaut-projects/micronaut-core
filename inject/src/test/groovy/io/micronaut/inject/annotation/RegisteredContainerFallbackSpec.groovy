package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Specification

import java.lang.annotation.RetentionPolicy

class RegisteredContainerFallbackSpec extends Specification {

    private static final String CONTAINER = OldStyle.name + 'Container'

    void "metadata storing an annotation directly answers for it after a container is registered for it"() {
        given: 'metadata compiled before the container was declared'
        def old = new DefaultAnnotationMetadata(
            [(OldStyle.name): [value: 'direct']] as Map, null, null,
            [(OldStyle.name): [value: 'direct']] as Map, null)
        def mutable = new MutableAnnotationMetadata()
        mutable.addDeclaredAnnotation(OldStyle.name, [value: 'mutable'])

        and: 'metadata compiled with the container, registering it globally'
        def container = OldStyle.name + 'Container'
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): container])
        def held = new DefaultAnnotationMetadata(
            [(container): [value: [AnnotationValue.builder(OldStyle).value('held').build()] as AnnotationValue[]]] as Map, null, null,
            [(container): [value: [AnnotationValue.builder(OldStyle).value('held').build()] as AnnotationValue[]]] as Map, null)

        expect: 'the old metadata reads its own entry'
        old.hasAnnotation(OldStyle)
        old.hasDeclaredAnnotation(OldStyle)
        old.stringValue(OldStyle).get() == 'direct'
        old.getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['direct']
        old.synthesize(OldStyle).value() == 'direct'
        old.findRepeatableAnnotation(OldStyle).get() == container
        mutable.stringValue(OldStyle).get() == 'mutable'

        and: 'the new metadata reads the container'
        held.hasAnnotation(OldStyle)
        held.getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['held']
        held.findRepeatableAnnotation(OldStyle).get() == container

        and: 'a hierarchy of both answers for each layer'
        new AnnotationMetadataHierarchy(old).stringValue(OldStyle).get() == 'direct'
    }

    private static DefaultAnnotationMetadata direct() {
        new DefaultAnnotationMetadata(
            [(OldStyle.name): [value: 'direct']] as Map, null, null,
            [(OldStyle.name): [value: 'direct']] as Map, null)
    }

    private static DefaultAnnotationMetadata held() {
        def values = [(CONTAINER): [value: [AnnotationValue.builder(OldStyle).value('held').build()] as AnnotationValue[]]] as Map
        new DefaultAnnotationMetadata(values, null, null, values, null)
    }

    void "a hierarchy with an unrelated layer still finds the direct annotation by class"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def unrelated = new DefaultAnnotationMetadata([Other: [:]] as Map, null, null, [Other: [:]] as Map, null)
        def hierarchy = new AnnotationMetadataHierarchy(direct(), unrelated)

        expect:
        hierarchy.hasAnnotation(OldStyle.name)
        hierarchy.getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['direct']
        hierarchy.hasAnnotation(OldStyle)
    }

    void "a hierarchy still finds the direct declared annotation when a parent uses the container"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def hierarchy = new AnnotationMetadataHierarchy(held(), direct())

        expect:
        hierarchy.hasDeclaredAnnotation(OldStyle.name)
        hierarchy.hasDeclaredAnnotation(OldStyle)
        hierarchy.getDeclaredAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['direct']
    }

    void "one metadata instance finds its direct declaration when an inherited annotation uses the container"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def declared = [(OldStyle.name): [value: 'direct']] as Map
        def all = declared + [(CONTAINER): [value: [AnnotationValue.builder(OldStyle).value('held').build()] as AnnotationValue[]]]
        def metadata = new DefaultAnnotationMetadata(declared, null, null, all, null)

        expect:
        metadata.hasDeclaredAnnotation(OldStyle.name)
        metadata.hasDeclaredAnnotation(OldStyle)
        metadata.getDeclaredAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['direct']
        metadata.stringValue(OldStyle).get() == 'direct'
    }

    void "merging a hierarchy retains both the direct and contained occurrence"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def hierarchy = new AnnotationMetadataHierarchy(held(), direct())

        expect:
        hierarchy.getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['direct', 'held']
        hierarchy.merge().getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['direct', 'held']
        hierarchy.merge().stringValue(OldStyle).get() == 'direct'

        and: 'the nearest layer still wins when the order is reversed'
        def reversed = new AnnotationMetadataHierarchy(direct(), held())
        reversed.merge().getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['held', 'direct']
        reversed.merge().stringValue(OldStyle).get() == 'held'
    }
    void "normalizing a direct source-only annotation during a merge preserves its retention"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def source = new MutableAnnotationMetadata()
        source.addDeclaredAnnotation(OldStyle.name, [value: 'source'], RetentionPolicy.SOURCE)

        when:
        def merged = new AnnotationMetadataHierarchy(source).merge()

        then:
        merged.getSourceRetentionAnnotations().contains(CONTAINER)
        merged.getAnnotationValuesByType(OldStyle).first().retentionPolicy == RetentionPolicy.SOURCE
    }
    void "normalizing a direct annotation during a merge preserves its recorded defaults"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def source = new MutableAnnotationMetadata()
        source.addDefaultAnnotationValues(OldStyle.name, [message: 'default message'], RetentionPolicy.RUNTIME)
        source.addDeclaredAnnotation(OldStyle.name, [value: 'direct'])

        when:
        def merged = new AnnotationMetadataHierarchy(source).merge()

        then:
        merged.getAnnotationValuesByType(OldStyle).first().get('message', String).get() == 'default message'
    }

    void "a lookup made before a container is registered does not hide the occurrences of old container-only metadata"() {
        given: 'metadata compiled before the remapper, storing the container as an ordinary annotation'
        def name = OldStyle.name + 'LateContainer'
        def values = [(name): [value: [AnnotationValue.builder('io.micronaut.inject.annotation.LateOld').value('held').build()] as AnnotationValue[]]] as Map
        def old = new DefaultAnnotationMetadata(values, null, null, values, null)

        expect: 'the occurrence type is not repeatable yet'
        old.getAnnotationValuesByType(LateOld).isEmpty()

        when: 'a class initialized later registers the container'
        AnnotationMetadataSupport.registerRepeatableAnnotations([(LateOld.name): name])

        then: 'the cached miss is dropped'
        old.getAnnotationValuesByType(LateOld)*.stringValue()*.get() == ['held']
        old.hasAnnotation(LateOld)
    }

    void "a lookup by name lists the occurrences from stereotypes before the declared ones, as it did before"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def declared = [(CONTAINER): [value: [AnnotationValue.builder(OldStyle).value('a').build()] as AnnotationValue[]]] as Map
        def stereotypes = [(CONTAINER): [value: [AnnotationValue.builder(OldStyle).value('b').build()] as AnnotationValue[]]] as Map
        def metadata = new DefaultAnnotationMetadata(declared, stereotypes, stereotypes, declared, null)

        expect:
        metadata.getAnnotationValuesByName(OldStyle.name)*.stringValue()*.get() == ['b', 'a']
    }
    void "merging a non-runtime annotation stored directly keeps its recorded defaults"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotations([(OldStyle.name): CONTAINER])
        def source = new MutableAnnotationMetadata()
        source.addDeclaredAnnotation(OldStyle.name, [value: 'direct'], RetentionPolicy.CLASS)
        source.addDefaultAnnotationValues(OldStyle.name, [extra: 'fallback'], RetentionPolicy.CLASS)
        def target = new MutableAnnotationMetadata()

        when:
        target.addAnnotationMetadata((DefaultAnnotationMetadata) source)

        then:
        target.getAnnotationValuesByType(OldStyle)*.defaultValues*.get('extra') == ['fallback']
    }
}

@interface LateOld {
    String value()
}
