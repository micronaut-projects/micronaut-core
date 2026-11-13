package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Specification

class RegisteredContainerFallbackSpec extends Specification {

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
        !old.findRepeatableAnnotation(OldStyle).isPresent()
        mutable.stringValue(OldStyle).get() == 'mutable'

        and: 'the new metadata reads the container'
        held.hasAnnotation(OldStyle)
        held.getAnnotationValuesByType(OldStyle)*.stringValue()*.get() == ['held']
        held.findRepeatableAnnotation(OldStyle).get() == container

        and: 'a hierarchy of both answers for each layer'
        new AnnotationMetadataHierarchy(old).stringValue(OldStyle).get() == 'direct'
    }
}
