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
import io.micronaut.inject.ast.MemberElement
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType

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
    <T extends Annotation> Element annotate(@Nonnull String annotationType, @Nonnull Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType)
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType)
        consumer?.accept(builder)
        def av = builder.build()
        this.annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                this.annotationMetadata,
                av.annotationName,
                av.values
        )
        String declaringTypeName = this instanceof MemberElement ? ((MemberElement) this).getOwningType().getName() : getName()
        AbstractAnnotationMetadataBuilder.addMutatedMetadata(
                declaringTypeName,
                annotatedNode,
                this.annotationMetadata
        )
        AstAnnotationUtils.invalidateCache(annotatedNode)
        return this
    }

    @CompileStatic
    protected Map<String, ClassNode> alignNewGenericsInfo(
            @Nonnull GenericsType[] genericsTypes,
            Map<String, ClassNode> genericsSpec) {
        Map<String, ClassNode> newSpec = new HashMap<>(genericsSpec.size())
        for (GenericsType genericsType : genericsTypes) {
            String name = genericsType.getName()
            ClassNode cn = genericsSpec.get(name)
            toNewGenericSpec(genericsSpec, newSpec, name, cn)
        }
        return newSpec
    }

    @CompileStatic
    private void toNewGenericSpec(Map<String, ClassNode> genericsSpec, Map<String, ClassNode> newSpec, String name, ClassNode cn) {
        if (cn != null) {
            newSpec.put(name, cn)
            if (cn.isGenericsPlaceHolder()) {
                String n = cn.getUnresolvedName()
                ClassNode resolved = genericsSpec.get(n)
                toNewGenericSpec(genericsSpec, newSpec, n, resolved)
            }
        }
    }
}
