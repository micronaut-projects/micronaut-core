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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.*;

import javax.lang.model.element.*;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
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

    @NonNull
    @Override
    public JavaClassElement newClassElement(
            @NonNull TypeElement type,
            @NonNull AnnotationMetadata annotationMetadata) {
        ElementKind kind = type.getKind();
        if (kind == ElementKind.ENUM) {
            return new JavaEnumElement(
                    type,
                    annotationMetadata,
                    visitorContext
            );
        } else {
            return new JavaClassElement(
                    type,
                    annotationMetadata,
                    visitorContext
            );
        }
    }

    @NonNull
    @Override
    public JavaMethodElement newMethodElement(
            ClassElement declaringClass,
            @NonNull ExecutableElement method,
            @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof JavaClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaClassElement");
        }
        return new JavaMethodElement(
                (JavaClassElement) declaringClass,
                method,
                annotationMetadata,
                visitorContext
        );
    }

    /**
     * Constructs a method method element with the given generic type information.
     *
     * @param declaringClass     The declaring class
     * @param method             The method
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic type info
     * @return The method element
     */
    public JavaMethodElement newMethodElement(
            ClassElement declaringClass,
            @NonNull ExecutableElement method,
            @NonNull AnnotationMetadata annotationMetadata,
            @Nullable Map<String, Map<String, TypeMirror>> genericTypes) {
        if (!(declaringClass instanceof JavaClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaClassElement");
        }
        final JavaClassElement javaDeclaringClass = (JavaClassElement) declaringClass;
        final JavaVisitorContext javaVisitorContext = visitorContext;

        return new JavaMethodElement(
                javaDeclaringClass,
                method,
                annotationMetadata,
                javaVisitorContext
        ) {
            @NonNull
            @Override
            protected JavaParameterElement newParameterElement(@NonNull VariableElement variableElement, @NonNull AnnotationMetadata annotationMetadata1) {
                return new JavaParameterElement(javaDeclaringClass, variableElement, annotationMetadata1, javaVisitorContext) {
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
    public JavaConstructorElement newConstructorElement(ClassElement declaringClass, @NonNull ExecutableElement constructor, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof JavaClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaClassElement");
        }
        return new JavaConstructorElement(
                (JavaClassElement) declaringClass,
                constructor,
                annotationMetadata,
                visitorContext
        );
    }

    @NonNull
    @Override
    public JavaFieldElement newFieldElement(ClassElement declaringClass, @NonNull VariableElement field, @NonNull AnnotationMetadata annotationMetadata) {
        return new JavaFieldElement(
                declaringClass,
                field,
                annotationMetadata,
                visitorContext
        );
    }

    @NonNull
    @Override
    public JavaFieldElement newFieldElement(@NonNull VariableElement field, @NonNull AnnotationMetadata annotationMetadata) {
        return new JavaFieldElement(
                field,
                annotationMetadata,
                visitorContext
        );
    }

    /**
     * Creates a new parameter element for the given arguments.
     * @param declaringClass The declaring class
     * @param field The field
     * @param annotationMetadata The annotation metadata
     * @return The parameter element
     */
    @NonNull
    public JavaParameterElement newParameterElement(ClassElement declaringClass, @NonNull VariableElement field, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof JavaClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a JavaClassElement");
        }
        return new JavaParameterElement((JavaClassElement) declaringClass, field, annotationMetadata, visitorContext);
    }
}
