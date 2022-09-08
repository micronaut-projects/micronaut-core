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

import io.micronaut.ast.groovy.annotation.GroovyElementAnnotationMetadataFactory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
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
public class GroovyElementFactory implements ElementFactory<AnnotatedNode, ClassNode, MethodNode, FieldNode> {
    private final GroovyVisitorContext visitorContext;

    public GroovyElementFactory(GroovyVisitorContext groovyVisitorContext) {
        this.visitorContext = groovyVisitorContext;
    }

    private ElementAnnotationMetadataFactory defaultAnnotationMetadata(Object nativeType,
                                                                       AnnotationMetadata annotationMetadata) {
        GroovyElementAnnotationMetadataFactory elementAnnotationMetadataFactory = visitorContext.getElementAnnotationMetadataFactory();
        return elementAnnotationMetadataFactory.overrideForNativeType(nativeType, element -> elementAnnotationMetadataFactory.build(element, annotationMetadata));
    }

    @Override
    public ClassElement newClassElement(ClassNode classNode, ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newClassElement(componentType, annotationMetadataFactory);
            return componentElement.toArray();
        }
        if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        }
        if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, annotationMetadataFactory);
        }
        if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, classNode, annotationMetadataFactory);
        }
        if (classNode.isGenericsPlaceHolder()) {
            return new GroovyGenericPlaceholderElement(visitorContext, classNode, annotationMetadataFactory, 0);
        } else {
            return new GroovyClassElement(visitorContext, classNode, annotationMetadataFactory);
        }
    }

    @NonNull
    @Override
    public ClassElement newClassElement(ClassNode classNode,
                                        ElementAnnotationMetadataFactory annotationMetadataFactory,
                                        Map<String, ClassElement> resolvedGenerics) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newClassElement(componentType, annotationMetadataFactory);
            return componentElement.toArray();
        }
        if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        }
        if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, annotationMetadataFactory) {
                @NonNull
                @Override
                public Map<String, ClassElement> getTypeArguments() {
                    if (resolvedGenerics != null) {
                        return resolvedGenerics;
                    }
                    return super.getTypeArguments();
                }
            };
        }
        if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, classNode, annotationMetadataFactory);
        }
        return new GroovyClassElement(visitorContext, classNode, annotationMetadataFactory) {
            @NonNull
            @Override
            public Map<String, ClassElement> getTypeArguments() {
                if (resolvedGenerics != null) {
                    return resolvedGenerics;
                }
                return super.getTypeArguments();
            }
        };
    }

    @NonNull
    @Override
    public GroovyMethodElement newMethodElement(ClassElement owningType,
                                                MethodNode method,
                                                ElementAnnotationMetadataFactory elementAnnotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyMethodElement(
            (GroovyClassElement) owningType,
            visitorContext,
            method,
            elementAnnotationMetadataFactory
        );
    }

    @NonNull
    @Override
    public ClassElement newSourceClassElement(ClassNode classNode, ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newSourceClassElement(componentType, annotationMetadataFactory);
            return componentElement.toArray();
        } else if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        } else if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, annotationMetadataFactory) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new GroovyBeanDefinitionBuilder(
                        this,
                        type,
                        ConfigurationMetadataBuilder.INSTANCE,
                        annotationMetadataFactory,
                        visitorContext
                    );
                }
            };
        } else {
            return new GroovyClassElement(visitorContext, classNode, annotationMetadataFactory) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new GroovyBeanDefinitionBuilder(
                        this,
                        type,
                        ConfigurationMetadataBuilder.INSTANCE,
                        annotationMetadataFactory,
                        visitorContext
                    );
                }
            };
        }
    }

    @Override
    public GroovyMethodElement newSourceMethodElement(ClassElement owningType,
                                                MethodNode method,
                                                ElementAnnotationMetadataFactory elementAnnotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyMethodElement(
            (GroovyClassElement) owningType,
            visitorContext,
            method,
            elementAnnotationMetadataFactory
        ) {
            @NonNull
            @Override
            public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                return new GroovyBeanDefinitionBuilder(
                    this,
                    type,
                    ConfigurationMetadataBuilder.INSTANCE,
                    elementAnnotationMetadataFactory,
                    visitorContext
                );
            }
        };
    }

    @NonNull
    @Override
    public ConstructorElement newConstructorElement(ClassElement declaringClass,
                                                    MethodNode constructor,
                                                    ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        if (!(constructor instanceof ConstructorNode)) {
            throw new IllegalArgumentException("Constructor must be a ConstructorNode");
        }
        return new GroovyConstructorElement(
            (GroovyClassElement) declaringClass,
            visitorContext,
            (ConstructorNode) constructor,
            annotationMetadataFactory
        );
    }

    @Override
    public EnumConstantElement newEnumConstantElement(ClassElement declaringClass,
                                                      FieldNode enumConstant,
                                                      ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyEnumElement");
        }
        return new GroovyEnumConstantElement(
            (GroovyClassElement) declaringClass,
            visitorContext,
            enumConstant,
            enumConstant,
            annotationMetadataFactory
        );
    }

    @NonNull
    @Override
    public GroovyFieldElement newFieldElement(ClassElement owningType,
                                              FieldNode field,
                                              ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningType instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyFieldElement(visitorContext, (GroovyClassElement) owningType, field, annotationMetadataFactory);
    }

}
