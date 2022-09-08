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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.JavaElementAnnotationMetadataFactory;
import io.micronaut.annotation.processing.PostponeToNextRoundException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An implementation of {@link ElementFactory} for Java.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public class JavaElementFactory implements ElementFactory<Element, TypeElement, ExecutableElement, VariableElement> {

    private final JavaVisitorContext visitorContext;

    public JavaElementFactory(JavaVisitorContext visitorContext) {
        this.visitorContext = Objects.requireNonNull(visitorContext, "Visitor context cannot be null");
    }

    private ElementAnnotationMetadataFactory defaultAnnotationMetadata(Object nativeType,
                                                                       AnnotationMetadata annotationMetadata) {
        JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory = visitorContext.getElementAnnotationMetadataFactory();
        return elementAnnotationMetadataFactory.overrideForNativeType(nativeType, element -> elementAnnotationMetadataFactory.build(element, annotationMetadata));
    }

    @NonNull
    @Override
    public JavaClassElement newClassElement(@NonNull TypeElement type,
                                            @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        ElementKind kind = type.getKind();
        switch (kind) {
            case ENUM:
                return new JavaEnumElement(
                    type,
                    annotationMetadataFactory,
                    visitorContext
                );
            case ANNOTATION_TYPE:
                return new JavaAnnotationElement(
                    type,
                    annotationMetadataFactory,
                    visitorContext
                );
            default:
                return new JavaClassElement(
                    type,
                    annotationMetadataFactory,
                    visitorContext
                );
        }
    }

    @NonNull
    @Override
    public ClassElement newClassElement(@NonNull TypeElement type,
                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                        @NonNull Map<String, ClassElement> resolvedGenerics) {
        ElementKind kind = type.getKind();
        switch (kind) {
            case ENUM:
                return new JavaEnumElement(
                    type,
                    annotationMetadataFactory,
                    visitorContext
                ) {
                    @NonNull
                    @Override
                    public Map<String, ClassElement> getTypeArguments() {
                        if (resolvedGenerics != null) {
                            return resolvedGenerics;
                        }
                        return super.getTypeArguments();
                    }
                };
            case ANNOTATION_TYPE:
                return new JavaAnnotationElement(type, annotationMetadataFactory, visitorContext);
            default:
                return new JavaClassElement(
                    type,
                    annotationMetadataFactory,
                    visitorContext
                ) {
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
    }

    @NonNull
    @Override
    public JavaClassElement newSourceClassElement(@NonNull TypeElement type, @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        ElementKind kind = type.getKind();
        if (kind == ElementKind.ENUM) {
            return new JavaEnumElement(
                type,
                annotationMetadataFactory,
                visitorContext
            ) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new JavaBeanDefinitionBuilder(
                        this,
                        type,
                        ConfigurationMetadataBuilder.INSTANCE,
                        annotationMetadataFactory,
                        visitorContext
                    );
                }
            };
        } else {
            return new JavaClassElement(
                type,
                annotationMetadataFactory,
                visitorContext
            ) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new JavaBeanDefinitionBuilder(
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

    @NonNull
    @Override
    public JavaMethodElement newSourceMethodElement(ClassElement declaringClass,
                                                    @NonNull ExecutableElement method,
                                                    @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        validateOwningClass(declaringClass);
        failIfPostponeIsNeeded(method);
        return new JavaMethodElement(
            (JavaClassElement) declaringClass,
            method,
            annotationMetadataFactory,
            visitorContext
        ) {
            @NonNull
            @Override
            public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                return new JavaBeanDefinitionBuilder(
                    this,
                    type,
                    ConfigurationMetadataBuilder.INSTANCE,
                    annotationMetadataFactory,
                    visitorContext
                );
            }
        };
    }

    @NonNull
    @Override
    public JavaMethodElement newMethodElement(ClassElement owningType,
                                              @NonNull ExecutableElement method,
                                              @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        validateOwningClass(owningType);
        failIfPostponeIsNeeded(method);
        return new JavaMethodElement(
            (JavaClassElement) owningType,
            method,
            annotationMetadataFactory,
            visitorContext
        );
    }

    /**
     * Constructs a method element with the given generic type information.
     *
     * @param declaringClass            The declaring class
     * @param method                    The method
     * @param annotationMetadataFactory The annotationMetadataFactory
     * @param genericTypes              The generic type info
     * @return The method element
     */
    public JavaMethodElement newMethodElement(ClassElement declaringClass,
                                              @NonNull ExecutableElement method,
                                              @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                              @Nullable Map<String, Map<String, TypeMirror>> genericTypes) {
        validateOwningClass(declaringClass);
        failIfPostponeIsNeeded(method);
        final JavaClassElement javaDeclaringClass = (JavaClassElement) declaringClass;
        final JavaVisitorContext javaVisitorContext = visitorContext;

        return new JavaMethodElement(
            javaDeclaringClass,
            method,
            annotationMetadataFactory,
            javaVisitorContext
        ) {
            @NonNull
            @Override
            protected JavaParameterElement newParameterElement(@NonNull MethodElement methodElement, @NonNull VariableElement variableElement) {
                return new JavaParameterElement(javaDeclaringClass, methodElement, variableElement, elementAnnotationMetadataFactory, javaVisitorContext) {
                    @NonNull
                    @Override
                    public ClassElement getGenericType() {
                        if (genericTypes != null) {
                            return parameterizedClassElement(getNativeType().asType(), javaVisitorContext, genericTypes);
                        } else {
                            return super.getGenericType();
                        }
                    }
                };
            }

            @Override
            @NonNull
            public ClassElement getGenericReturnType() {
                if (genericTypes != null) {
                    return super.returnType(genericTypes);
                } else {
                    return super.getGenericReturnType();
                }
            }
        };
    }

    @NonNull
    @Override
    public JavaConstructorElement newConstructorElement(ClassElement declaringClass,
                                                        @NonNull ExecutableElement constructor,
                                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        validateOwningClass(declaringClass);
        failIfPostponeIsNeeded(constructor);
        return new JavaConstructorElement(
            (JavaClassElement) declaringClass,
            constructor,
            annotationMetadataFactory,
            visitorContext
        );
    }

    @NonNull
    @Override
    public JavaEnumConstantElement newEnumConstantElement(ClassElement declaringClass,
                                                          @NonNull VariableElement enumConstant,
                                                          @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(declaringClass instanceof JavaEnumElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaEnumElement");
        }
        failIfPostponeIsNeeded(enumConstant);
        return new JavaEnumConstantElement(
            (JavaEnumElement) declaringClass,
            enumConstant,
            annotationMetadataFactory,
            visitorContext
        );
    }

    @NonNull
    @Override
    public JavaFieldElement newFieldElement(ClassElement declaringClass,
                                            @NonNull VariableElement field,
                                            @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        failIfPostponeIsNeeded(field);
        return new JavaFieldElement(
            (JavaClassElement) declaringClass,
            field,
            annotationMetadataFactory,
            visitorContext
        );
    }

    private void failIfPostponeIsNeeded(ExecutableElement executableElement) {
        List<? extends VariableElement> parameters = executableElement.getParameters();
        for (VariableElement parameter : parameters) {
            failIfPostponeIsNeeded(parameter);
        }
        TypeKind returnKind = executableElement.getReturnType().getKind();
        if (returnKind == TypeKind.ERROR) {
            throw new PostponeToNextRoundException();
        }
    }

    private void failIfPostponeIsNeeded(VariableElement  variableElement) {
        TypeMirror type = variableElement.asType();
        if (type.getKind() == TypeKind.ERROR) {
            throw new PostponeToNextRoundException();
        }
    }

    private static void validateOwningClass(ClassElement owningClass) {
        if (!(owningClass instanceof JavaClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaClassElement");
        }
    }
}
