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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

/**
 * A field element returning data from a {@link FieldNode}. The
 * variable could be a field or property node.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class GroovyFieldElement extends AbstractGroovyElement implements FieldElement {

    private final GroovyClassElement owningType;
    private final FieldNode fieldNode;
    @Nullable
    private GroovyClassElement declaringType;
    @Nullable
    private ClassElement type;
    @Nullable
    private ClassElement genericType;

    /**
     * @param visitorContext The visitor context
     * @param owningType The owningType
     * @param fieldNode The {@link FieldNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    GroovyFieldElement(GroovyVisitorContext visitorContext,
                       GroovyClassElement owningType,
                       FieldNode fieldNode,
                       ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, new GroovyNativeElement.Field(fieldNode, owningType.getNativeType()), annotationMetadataFactory);
        this.owningType = owningType;
        this.fieldNode = fieldNode;
    }

    @Override
    protected @NonNull AbstractGroovyElement copyConstructor() {
        return new GroovyFieldElement(visitorContext, owningType, fieldNode, elementAnnotationMetadataFactory);
    }

    @Override
    public FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public GroovyClassElement getOwningType() {
        return owningType;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return super.resolveModifiers(fieldNode);
    }

    @Override
    public String toString() {
        return fieldNode.getName();
    }

    @Override
    public ClassElement getGenericField() {
        return newClassElement(fieldNode.getType(), getDeclaringType().getTypeArguments());
    }

    @Override
    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    @Override
    public boolean isArray() {
        return getType().isArray();
    }

    @Override
    public int getArrayDimensions() {
        return getType().getArrayDimensions();
    }

    @Override
    public @NonNull String getName() {
        return fieldNode.getName();
    }

    @Override
    public boolean isAbstract() {
        return Modifier.isAbstract(fieldNode.getModifiers());
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(fieldNode.getModifiers());
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(fieldNode.getModifiers());
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(fieldNode.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(fieldNode.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(fieldNode.getModifiers());
    }

    @Override
    public boolean isPackagePrivate() {
        return !Modifier.isPublic(fieldNode.getModifiers()) && !Modifier.isProtected(fieldNode.getModifiers()) && !Modifier.isPrivate(fieldNode.getModifiers());
    }

    @Override
    public Object getConstantValue() {
        if (fieldNode.hasInitialExpression()
            && fieldNode.getInitialValueExpression() instanceof ConstantExpression constExpression) {
            return constExpression.getValue();
        }
        return null;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        if (type == null) {
            type = newClassElement(fieldNode.getType());
        }
        return type;
    }

    @Override
    public @NonNull ClassElement getGenericType() {
        if (genericType == null) {
            genericType = newClassElement(fieldNode.getType(), getDeclaringType().getTypeArguments());
        }
        return genericType;
    }

    @Override
    public GroovyClassElement getDeclaringType() {
        if (declaringType == null) {
            ClassNode declaringClass = fieldNode.getDeclaringClass();
            if (declaringClass == null) {
                throw new IllegalStateException("Declaring class could not be established");
            }
            if (owningType.getNativeType().annotatedNode().equals(declaringClass)) {
                declaringType = owningType;
            } else {
                Map<String, ClassElement> typeArguments = getOwningType().getTypeArguments(declaringClass.getName());
                declaringType = (GroovyClassElement) newClassElement(declaringClass, typeArguments);
            }
        }
        return declaringType;
    }
}
