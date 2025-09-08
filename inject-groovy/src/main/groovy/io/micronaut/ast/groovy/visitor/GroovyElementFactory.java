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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.Map;

/**
 * Implementation of {@link ElementFactory} for Groovy.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Internal
public class GroovyElementFactory implements ElementFactory<AnnotatedNode, ClassNode, MethodNode, FieldNode> {
    private final GroovyVisitorContext visitorContext;

    public GroovyElementFactory(GroovyVisitorContext groovyVisitorContext) {
        this.visitorContext = groovyVisitorContext;
    }

    @Override
    public @NonNull ClassElement newClassElement(ClassNode classNode, ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newClassElement(componentType, annotationMetadataFactory);
            return componentElement.toArray();
        }
        if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        }
        if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, new GroovyNativeElement.Class(classNode), annotationMetadataFactory);
        }
        if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, new GroovyNativeElement.Class(classNode), annotationMetadataFactory);
        }
        if (classNode.isGenericsPlaceHolder()) {
            throw new IllegalArgumentException("Placeholder cannot be created without declared element!");
        }
        return new GroovyClassElement(visitorContext, new GroovyNativeElement.Class(classNode), annotationMetadataFactory);
    }

    @NonNull
    @Override
    public ClassElement newClassElement(@NonNull ClassNode classNode,
                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                        @NonNull Map<String, ClassElement> resolvedGenerics) {
        if (CollectionUtils.isNotEmpty(resolvedGenerics)) {
            return newClassElement(classNode, annotationMetadataFactory).withTypeArguments(resolvedGenerics);
        }
        return newClassElement(classNode, annotationMetadataFactory);
    }

    @NonNull
    @Override
    public GroovyMethodElement newMethodElement(@NonNull ClassElement owningType,
                                                @NonNull MethodNode method,
                                                @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyMethodElement(
            (GroovyClassElement) owningType,
            visitorContext,
            new GroovyNativeElement.Method(method),
            method,
            elementAnnotationMetadataFactory
        );
    }

    @NonNull
    @Override
    public ClassElement newSourceClassElement(ClassNode classNode, @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newSourceClassElement(componentType, annotationMetadataFactory);
            return componentElement.toArray();
        } else if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        } else if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, new GroovyNativeElement.Class(classNode), annotationMetadataFactory) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new GroovyBeanDefinitionBuilder(
                        this,
                        type,
                        annotationMetadataFactory,
                        visitorContext
                    );
                }
            };
        } else {
            return new GroovyClassElement(visitorContext, new GroovyNativeElement.Class(classNode), annotationMetadataFactory) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new GroovyBeanDefinitionBuilder(
                        this,
                        type,
                        annotationMetadataFactory,
                        visitorContext
                    );
                }
            };
        }
    }

    @Override
    public @NonNull GroovyMethodElement newSourceMethodElement(@NonNull ClassElement owningType,
                                                               @NonNull MethodNode method,
                                                               @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyMethodElement(
            (GroovyClassElement) owningType,
            visitorContext,
            new GroovyNativeElement.Method(method),
            method,
            elementAnnotationMetadataFactory
        ) {
            @NonNull
            @Override
            public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                return new GroovyBeanDefinitionBuilder(
                    this,
                    type,
                    elementAnnotationMetadataFactory,
                    visitorContext
                );
            }
        };
    }

    @NonNull
    @Override
    public ConstructorElement newConstructorElement(@NonNull ClassElement owningType,
                                                    @NonNull MethodNode constructor,
                                                    @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        if (!(constructor instanceof ConstructorNode)) {
            throw new IllegalArgumentException("Constructor must be a ConstructorNode");
        }
        return new GroovyConstructorElement(
            (GroovyClassElement) owningType,
            visitorContext,
            new GroovyNativeElement.Method(constructor),
            (ConstructorNode) constructor,
            annotationMetadataFactory
        );
    }

    @Override
    public @NonNull EnumConstantElement newEnumConstantElement(@NonNull ClassElement declaringClass,
                                                               @NonNull FieldNode enumConstant,
                                                               @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyEnumElement");
        }
        return new GroovyEnumConstantElement(
            (GroovyClassElement) declaringClass,
            visitorContext,
            enumConstant,
            annotationMetadataFactory
        );
    }

    @NonNull
    @Override
    public GroovyFieldElement newFieldElement(@NonNull ClassElement owningType,
                                              @NonNull FieldNode field,
                                              @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyFieldElement(visitorContext, (GroovyClassElement) owningType, field, annotationMetadataFactory);
    }

}
