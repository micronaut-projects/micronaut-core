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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.FieldElement;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;

/**
 * A field element returning data from a {@link Variable}. The
 * variable could be a field or property node.
 *
 * @author James Kleeh
 * @since 1.0
 */
public abstract class AbstractGroovyVariableElement extends AbstractGroovyElement implements FieldElement {

    private final Variable variable;

    /**
     * @param visitorContext            The visitor context
     * @param variable                  The {@link Variable}
     * @param annotatedNode             The annotated ndoe
     * @param annotationMetadataFactory The annotation metadata
     */
    AbstractGroovyVariableElement(GroovyVisitorContext visitorContext,
                                  Variable variable,
                                  AnnotatedNode annotatedNode,
                                  ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, annotatedNode, annotationMetadataFactory);
        this.variable = variable;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        if (variable instanceof FieldNode) {
            return super.resolveModifiers(((FieldNode) variable));
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public String toString() {
        return variable.getName();
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
        return variable.getName();
    }

    @Override
    public boolean isAbstract() {
        return Modifier.isAbstract(variable.getModifiers());
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(variable.getModifiers());
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(variable.getModifiers());
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(variable.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(variable.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(variable.getModifiers());
    }

    @Override
    public boolean isPackagePrivate() {
        return !Modifier.isPublic(variable.getModifiers()) && !Modifier.isProtected(variable.getModifiers()) && !Modifier.isPrivate(variable.getModifiers());
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return visitorContext.getElementFactory().newClassElement(variable.getType(), elementAnnotationMetadataFactory);
    }

    @Override
    public GroovyClassElement getDeclaringType() {
        ClassNode declaringClass = null;
        if (variable instanceof FieldNode) {
            FieldNode fn = (FieldNode) variable;
            declaringClass = fn.getDeclaringClass();
        } else if (variable instanceof PropertyNode) {
            PropertyNode pn = (PropertyNode) variable;
            declaringClass = pn.getDeclaringClass();
        }

        if (declaringClass == null) {
            throw new IllegalStateException("Declaring class could not be established");
        }
        return (GroovyClassElement) visitorContext.getElementFactory().newClassElement(declaringClass, elementAnnotationMetadataFactory);
    }

    @Override
    public GroovyClassElement getOwningType() {
        return getDeclaringType();
    }
}
