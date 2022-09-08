/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.ConstructorNode;

/**
 * A {@link ConstructorElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
public class GroovyConstructorElement extends GroovyMethodElement implements ConstructorElement {
    /**
     * @param owningType                The owning class
     * @param visitorContext            The visitor context
     * @param methodNode                The {@link ConstructorNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    GroovyConstructorElement(GroovyClassElement owningType,
                             GroovyVisitorContext visitorContext,
                             ConstructorNode methodNode,
                             ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(owningType, visitorContext, methodNode, annotationMetadataFactory);
    }

    @Override
    protected AbstractGroovyElement copyThis() {
        return new GroovyConstructorElement(getOwningType(), visitorContext, (ConstructorNode) getNativeType(), elementAnnotationMetadataFactory);
    }

    @Override
    public ConstructorElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ConstructorElement) super.withAnnotationMetadata(annotationMetadata);
    }
}
