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
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of {@link PropertyElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class GroovyPropertyElement extends AbstractGroovyElement implements PropertyElement {
    private final String name;
    private final boolean readOnly;
    private final GroovyClassElement declaringElement;

    private final ClassElement type;
    @Nullable
    private final FieldNode fieldNode;
    @Nullable
    private final MethodNode readMethodNode;
    @Nullable
    private final MethodNode writeMethodNode;
    @Nullable
    private Optional<MethodElement> readMethod;
    @Nullable
    private Optional<MethodElement> writeMethod;
    @Nullable
    private Optional<FieldElement> field;

    /**
     * Default constructor.
     *
     * @param visitorContext            The visitor context
     * @param declaringElement          The declaring element
     * @param propertyNode              The property node
     * @param annotationMetadataFactory the annotation metadata
     */
    GroovyPropertyElement(GroovyVisitorContext visitorContext,
                          GroovyClassElement declaringElement,
                          PropertyNode propertyNode,
                          ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this(visitorContext,
            declaringElement,
            visitorContext.getElementFactory().newClassElement(propertyNode.getType(), annotationMetadataFactory),
            propertyNode.getName(),
            propertyNode.getField().isFinal(),
            propertyNode.getField(),
            propertyNode.getField(),
            null,
            null,
            annotationMetadataFactory);
    }

    GroovyPropertyElement(GroovyVisitorContext visitorContext,
                          GroovyClassElement declaringElement,
                          ClassElement type,
                          String name,
                          boolean isReadOnly,
                          AnnotatedNode nativeType,
                          FieldNode fieldNode,
                          MethodNode readMethodNode,
                          MethodNode writeMethodNode,
                          ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, nativeType, annotationMetadataFactory);
        this.fieldNode = fieldNode;
        this.readMethodNode = readMethodNode;
        this.writeMethodNode = writeMethodNode;
        this.type = type;
        this.name = name;
        this.readOnly = isReadOnly;
        this.declaringElement = declaringElement;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public Optional<FieldElement> getField() {
        if (field == null) {
            if (fieldNode != null) {
                field = Optional.of(new GroovyFieldElement(visitorContext, fieldNode, elementAnnotationMetadataFactory));
            } else {
                field = Optional.empty();
            }
        }
        return field;
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        if (writeMethod == null) {
            if (readOnly) {
                writeMethod = Optional.empty();
            } else if (writeMethodNode != null) {
                writeMethod = Optional.of(new GroovyMethodElement(
                    declaringElement,
                    visitorContext,
                    writeMethodNode,
                    elementAnnotationMetadataFactory
                ));
            } else {
                writeMethod = Optional.of(MethodElement.of(
                    declaringElement,
                    this,
                    PrimitiveElement.VOID,
                    PrimitiveElement.VOID,
                    NameUtils.setterNameFor(name),
                    ParameterElement.of(getType(), name)
                ));
            }
        }
        return writeMethod;
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        if (readMethod == null) {
            if (readMethodNode != null) {
                readMethod = Optional.of(new GroovyMethodElement(declaringElement, visitorContext, readMethodNode, elementAnnotationMetadataFactory));
            } else if (fieldNode != null) {
                ClassElement type = getType();
                String getterName = NameUtils.getterNameFor(
                    name,
                    type.equals(PrimitiveElement.BOOLEAN) || type.getName().equals(Boolean.class.getName())
                );
                readMethod = Optional.of(MethodElement.of(
                    declaringElement,
                    this,
                    getType(),
                    getGenericType(),
                    getterName
                ));
            } else {
                readMethod = Optional.empty();
            }
        }
        return readMethod;
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
    public String toString() {
        return getName();
    }

    @Override
    public GroovyClassElement getDeclaringType() {
        return declaringElement;
    }

    @Override
    public GroovyClassElement getOwningType() {
        return declaringElement;
    }
}
