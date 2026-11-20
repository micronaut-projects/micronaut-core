package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationValue
import jakarta.inject.Named
import spock.lang.Specification

import java.lang.annotation.Annotation

class AnnotationMetadataHierarchySubclassLayerSpec extends Specification {

    void "a layer that overrides the lookup by name answers the lookup by class"() {
        given:
        def layer = new DefaultAnnotationMetadata(null, null, null, null, null) {
            @Override
            <T extends Annotation> AnnotationValue<T> getAnnotation(String annotation) {
                return annotation == Named.name ? AnnotationValue.builder(annotation).value("supplied").build() : null
            }
        }
        def hierarchy = new AnnotationMetadataHierarchy(layer)

        expect:
        hierarchy.getAnnotation(Named).stringValue().get() == "supplied"
        hierarchy.findAnnotation(Named).get().stringValue().get() == "supplied"
        hierarchy.getAnnotation(Named.name).stringValue().get() == "supplied"
    }
}
