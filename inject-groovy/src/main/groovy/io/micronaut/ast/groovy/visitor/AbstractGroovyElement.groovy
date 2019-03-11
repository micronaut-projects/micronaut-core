/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.ast.groovy.visitor

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataDelegate
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.util.ArgumentUtils
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import io.micronaut.inject.ast.Element
import org.codehaus.groovy.ast.AnnotatedNode

import javax.annotation.Nonnull
import java.lang.annotation.Annotation
import java.util.function.Consumer

/**
 * Abstract Groovy element.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
abstract class AbstractGroovyElement implements AnnotationMetadataDelegate, Element {

    final AnnotatedNode annotatedNode
    AnnotationMetadata annotationMetadata

    AbstractGroovyElement(AnnotatedNode annotatedNode, AnnotationMetadata annotationMetadata) {
        this.annotatedNode = annotatedNode
        this.annotationMetadata = annotationMetadata
    }

    @CompileStatic
    @Override
    def <T extends Annotation> Element annotate(@Nonnull String annotationType, @Nonnull Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType)
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType)
        consumer?.accept(builder)
        def av = builder.build()
        this.annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                this.annotationMetadata,
                av.annotationName,
                av.values
        )
        AbstractAnnotationMetadataBuilder.addMutatedMetadata(
                annotatedNode,
                this.annotationMetadata
        )
        AstAnnotationUtils.invalidateCache(annotatedNode)
        return this
    }
}
