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

import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.PropertyElement;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.PropertyNode;

import java.util.Collections;
import java.util.Set;

/**
 * Implementation of {@link PropertyElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
abstract class GroovyPropertyElement extends AbstractGroovyElement implements PropertyElement {

    private final String name;
    private final boolean readOnly;
    private final Object nativeType;
    private final GroovyClassElement declaringClass;
    private ClassElement declaringElement;

    /**
     * Default constructor.
     *
     * @param visitorContext The visitor context
     * @param declaringClass The declaring class
     * @param annotatedNode    The annotated node
     * @param annotationMetadata the annotation metadata
     * @param name the name
     * @param readOnly Whether it is read only
     * @param nativeType the native underlying type
     */
    GroovyPropertyElement(
            GroovyVisitorContext visitorContext,
            GroovyClassElement declaringClass,
            AnnotatedNode annotatedNode,
            AnnotationMetadata annotationMetadata,
            String name,
            boolean readOnly,
            Object nativeType) {
        super(visitorContext, annotatedNode, annotationMetadata);
        this.name = name;
        this.readOnly = readOnly;
        this.nativeType = nativeType;
        this.declaringClass = declaringClass;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        if (isReadOnly()) {
            return CollectionUtils.setOf(ElementModifier.FINAL, ElementModifier.PUBLIC);
        } else {
            return Collections.singleton(ElementModifier.PUBLIC);
        }
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Object getNativeType() {
        return nativeType;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ClassElement getDeclaringType() {
        if (declaringElement == null && nativeType instanceof PropertyNode) {
            PropertyNode propertyNode = (PropertyNode) nativeType;
            declaringElement = visitorContext.getElementFactory().newClassElement(
                propertyNode.getDeclaringClass(),
                AstAnnotationUtils.getAnnotationMetadata(
                    sourceUnit,
                    compilationUnit,
                    propertyNode.getDeclaringClass()
                )
            );
        }
        if (declaringElement != null) {
            return declaringElement;
        }
        return declaringClass;
    }

    @Override
    public ClassElement getOwningType() {
        return declaringClass;
    }

}
