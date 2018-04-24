package io.micronaut.ast.groovy.visitor

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataDelegate
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.visitor.Element

import javax.annotation.Nullable
import java.lang.annotation.Annotation

abstract class AbstractGroovyElement implements AnnotationMetadataDelegate, Element {

    final AnnotationMetadata annotationMetadata

    AbstractGroovyElement(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata
    }

}
