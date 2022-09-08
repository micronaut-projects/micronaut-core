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
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.FieldElement;
import org.apache.groovy.util.concurrent.LazyInitializable;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * A field element returning data from a {@link FieldNode}. The
 * variable could be a field or property node.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyFieldElement extends AbstractGroovyElement implements FieldElement {

    private final GroovyClassElement owningType;
    private final FieldNode fieldNode;

    /**
     * @param visitorContext            The visitor context
     * @param owningType                The owningType
     * @param fieldNode                 The {@link FieldNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    GroovyFieldElement(GroovyVisitorContext visitorContext,
                       GroovyClassElement owningType,
                       FieldNode fieldNode,
                       ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, fieldNode, annotationMetadataFactory);
        this.owningType = owningType;
        this.fieldNode = unwrapLazyField(fieldNode);
    }

    @Override
    protected AbstractGroovyElement copyThis() {
        return new GroovyFieldElement(visitorContext, owningType, fieldNode, elementAnnotationMetadataFactory);
    }

    @Override
    public FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Deprecated
    @NextMajorVersion("This should be removed")
    private static FieldNode unwrapLazyField(FieldNode fieldNode) {
        if (fieldNode instanceof LazyInitializable) {
            //this nonsense is to work around https://issues.apache.org/jira/browse/GROOVY-10398
            ((LazyInitializable) fieldNode).lazyInit();
            try {
                Field delegate = fieldNode.getClass().getDeclaredField("delegate");
                delegate.setAccessible(true);
                fieldNode = (FieldNode) delegate.get(fieldNode);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // no op
            }
        }
        return fieldNode;
    }

    @Override
    public FieldNode getNativeType() {
        return fieldNode;
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
        if (isPrimitive()) {
            ClassNode cn = ClassHelper.make(ClassUtils.getPrimitiveType(getType().getName()).orElse(null));
            if (cn != null) {
                return new GroovyClassElement(visitorContext, cn, elementAnnotationMetadataFactory) {

                    @Override
                    public boolean isPrimitive() {
                        return true;
                    }
                };
            } else {
                return getGenericType();
            }
        }
        return new GroovyClassElement(visitorContext, (ClassNode) getGenericType().getNativeType(), elementAnnotationMetadataFactory);
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
    public String getName() {
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

    @NonNull
    @Override
    public ClassElement getType() {
        return visitorContext.getElementFactory().newClassElement(fieldNode.getType(), elementAnnotationMetadataFactory);
    }

    @Override
    public GroovyClassElement getDeclaringType() {
        ClassNode declaringClass = fieldNode.getDeclaringClass();
        if (declaringClass == null) {
            throw new IllegalStateException("Declaring class could not be established");
        }
        return (GroovyClassElement) visitorContext.getElementFactory().newClassElement(declaringClass, elementAnnotationMetadataFactory);
    }
}
