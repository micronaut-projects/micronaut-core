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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.PropertyNode;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of {@link PropertyElement} for Groovy.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
final class GroovyNativePropertyElement extends AbstractGroovyElement implements PropertyElement {
    private final String name;
    private final GroovyClassElement owningType;

    private final ClassElement type;
    private final PropertyNode propertyNode;
    @Nullable
    private MethodElement getter;
    @Nullable
    private MethodElement setter;
    @Nullable
    private FieldElement field;

    /**
     * Default constructor.
     *
     * @param visitorContext            The visitor context
     * @param owningType             The owning element
     * @param propertyNode              The property node
     * @param annotationMetadataFactory the annotation metadata
     */
    GroovyNativePropertyElement(GroovyVisitorContext visitorContext,
                                GroovyClassElement owningType,
                                PropertyNode propertyNode,
                                ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, propertyNode, annotationMetadataFactory);
        this.propertyNode = propertyNode;
        this.owningType = owningType;
        this.name = propertyNode.getName();
        this.type = new GroovyClassElement(visitorContext, propertyNode.getType(), annotationMetadataFactory);
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public Optional<FieldElement> getField() {
        if (field == null) {
            if (propertyNode.getField() != null) {
                field = new GroovyFieldElement(visitorContext, owningType, propertyNode.getField(), elementAnnotationMetadataFactory);
            }
        }
        return Optional.ofNullable(field);
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        if (setter == null) {
            if (!isReadOnly()) {
                setter = MethodElement.of(
                    owningType,
                    this,
                    PrimitiveElement.VOID,
                    PrimitiveElement.VOID,
                    NameUtils.setterNameFor(name),
                    ParameterElement.of(getType(), name)
                );
            }
        }
        return Optional.ofNullable(setter);
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        if (getter == null) {
            if (propertyNode.getField() != null) {
                ClassElement type = getType();
                String getterName = NameUtils.getterNameFor(
                    name,
                    type.equals(PrimitiveElement.BOOLEAN) || type.getName().equals(Boolean.class.getName())
                );
                getter = MethodElement.of(
                    owningType,
                    this,
                    getType(),
                    getGenericType(),
                    getterName
                );
            }
        }
        return Optional.ofNullable(getter);
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
    public String getName() {
        return name;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return propertyNode.isPublic();
    }

    @Override
    public boolean isPrivate() {
        return propertyNode.isPrivate();
    }

    @Override
    public boolean isStatic() {
        return propertyNode.isStatic();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public AccessKind getReadAccessKind() {
        return AccessKind.METHOD;
    }

    @Override
    public AccessKind getWriteAccessKind() {
        return AccessKind.METHOD;
    }

    @Override
    public boolean isReadOnly() {
        return propertyNode.getField().isFinal();
    }

    @Override
    public boolean isWriteOnly() {
        return false;
    }

    @Override
    public ClassElement getDeclaringType() {
        ClassNode declaringClass = propertyNode.getDeclaringClass();
        if (declaringClass == null) {
            return owningType;
        }
        return new GroovyClassElement(visitorContext, declaringClass, elementAnnotationMetadataFactory);
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }
}
