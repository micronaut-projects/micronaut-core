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

import io.micronaut.annotation.processing.PostponeToNextRoundException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
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
@Internal
public class JavaElementFactory implements ElementFactory<Element, TypeElement, ExecutableElement, VariableElement> {

    private final JavaVisitorContext visitorContext;

    public JavaElementFactory(JavaVisitorContext visitorContext) {
        this.visitorContext = Objects.requireNonNull(visitorContext, "Visitor context cannot be null");
    }

    @NonNull
    @Override
    public JavaClassElement newClassElement(@NonNull TypeElement type,
                                            @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        ElementKind kind = type.getKind();
        return switch (kind) {
            case ENUM -> new JavaEnumElement(
                new JavaNativeElement.Class(type),
                annotationMetadataFactory,
                visitorContext
            );
            case ANNOTATION_TYPE -> new JavaAnnotationElement(
                new JavaNativeElement.Class(type),
                annotationMetadataFactory,
                visitorContext
            );
            default -> new JavaClassElement(
                new JavaNativeElement.Class(type),
                annotationMetadataFactory,
                visitorContext
            );
        };
    }

    @NonNull
    @Override
    public ClassElement newClassElement(@NonNull TypeElement type,
                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                        @NonNull Map<String, ClassElement> resolvedGenerics) {
        if (resolvedGenerics.isEmpty()) {
            return newClassElement(type, annotationMetadataFactory);
        }
        return newClassElement(type, annotationMetadataFactory).withTypeArguments(resolvedGenerics);
    }

    @NonNull
    @Override
    public JavaClassElement newSourceClassElement(@NonNull TypeElement type, @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        ElementKind kind = type.getKind();
        if (kind == ElementKind.ENUM) {
            return new JavaEnumElement(
                new JavaNativeElement.Class(type),
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
                new JavaNativeElement.Class(type),
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
    public JavaMethodElement newSourceMethodElement(@NonNull ClassElement declaringClass,
                                                    @NonNull ExecutableElement method,
                                                    @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        validateOwningClass(declaringClass);
        failIfPostponeIsNeeded(declaringClass, method);
        return new JavaMethodElement(
            (JavaClassElement) declaringClass,
            new JavaNativeElement.Method(method),
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
    public JavaMethodElement newMethodElement(@NonNull ClassElement owningType,
                                              @NonNull ExecutableElement method,
                                              @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        validateOwningClass(owningType);
        failIfPostponeIsNeeded(owningType, method);
        return new JavaMethodElement(
            (JavaClassElement) owningType,
            new JavaNativeElement.Method(method),
            annotationMetadataFactory,
            visitorContext
        );
    }

    @NonNull
    @Override
    public JavaConstructorElement newConstructorElement(@NonNull ClassElement owningType,
                                                        @NonNull ExecutableElement constructor,
                                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        validateOwningClass(owningType);
        failIfPostponeIsNeeded(owningType, constructor);
        return new JavaConstructorElement(
            (JavaClassElement) owningType,
            new JavaNativeElement.Method(constructor),
            annotationMetadataFactory,
            visitorContext
        );
    }

    @NonNull
    @Override
    public JavaEnumConstantElement newEnumConstantElement(@NonNull ClassElement owningType,
                                                          @NonNull VariableElement enumConstant,
                                                          @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningType instanceof JavaEnumElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaEnumElement");
        }
        failIfPostponeIsNeeded(owningType, enumConstant);
        return new JavaEnumConstantElement(
            (JavaEnumElement) owningType,
            new JavaNativeElement.Variable(enumConstant),
            annotationMetadataFactory,
            visitorContext
        );
    }

    @NonNull
    @Override
    public JavaFieldElement newFieldElement(@NonNull ClassElement owningType,
                                            @NonNull VariableElement field,
                                            @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        failIfPostponeIsNeeded(owningType, field);
        return new JavaFieldElement(
            (JavaClassElement) owningType,
            new JavaNativeElement.Variable(field),
            annotationMetadataFactory,
            visitorContext
        );
    }

    private void failIfPostponeIsNeeded(TypedElement member, ExecutableElement executableElement) {
        List<? extends VariableElement> parameters = executableElement.getParameters();
        for (VariableElement parameter : parameters) {
            failIfPostponeIsNeeded(member, parameter);
        }
        TypeMirror returnType = executableElement.getReturnType();
        TypeKind returnKind = returnType.getKind();
        if (returnKind == TypeKind.ERROR) {
            throw new PostponeToNextRoundException(member, member.getName() + " " + executableElement);
        }
    }

    private void failIfPostponeIsNeeded(TypedElement member, VariableElement variableElement) {
        TypeMirror type = variableElement.asType();
        if (type.getKind() == TypeKind.ERROR) {
            throw new PostponeToNextRoundException(member, member.getName() + " " + variableElement);
        }
    }

    private static void validateOwningClass(ClassElement owningClass) {
        if (!(owningClass instanceof JavaClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaClassElement");
        }
    }
}
