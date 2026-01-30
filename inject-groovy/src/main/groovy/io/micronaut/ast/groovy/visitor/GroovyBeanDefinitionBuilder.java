/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.beans.BeanParameterElement;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import org.jspecify.annotations.NonNull;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Groovy version implementation of {@link AbstractBeanDefinitionBuilder}.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
class GroovyBeanDefinitionBuilder extends AbstractBeanDefinitionBuilder {
    private final GroovyVisitorContext visitorContext;
    private final GroovyAnnotationMetadataBuilder annotationBuilder;

    /**
     * Default constructor.
     *
     * @param originatingElement The originating element
     * @param beanType The bean type
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @param visitorContext the visitor context
     */
    GroovyBeanDefinitionBuilder(
        Element originatingElement,
        ClassElement beanType,
        ElementAnnotationMetadataFactory elementAnnotationMetadataFactory,
        GroovyVisitorContext visitorContext) {
        super(originatingElement, beanType, visitorContext, elementAnnotationMetadataFactory);
        if (getClass() == GroovyBeanDefinitionBuilder.class) {
            visitorContext.addBeanDefinitionBuilder(this);
        }
        this.visitorContext = visitorContext;
        this.annotationBuilder = visitorContext.getAnnotationMetadataBuilder();
    }

    @Override
    protected @NonNull AbstractBeanDefinitionBuilder createChildBean(FieldElement producerField) {
        final ClassElement parentType = getBeanType();
        return new GroovyBeanDefinitionBuilder(
            GroovyBeanDefinitionBuilder.this.getOriginatingElement(),
            producerField.getGenericField().getType(),
            elementAnnotationMetadataFactory,
            GroovyBeanDefinitionBuilder.this.visitorContext
        ) {
            @Override
            public @NonNull Element getProducingElement() {
                return producerField;
            }

            @Override
            public @NonNull ClassElement getDeclaringElement() {
                return producerField.getDeclaringType();
            }

            @Override
            protected ElementBeanDefinitionBuilder createBeanDefinitionBuilder(ElementBeanDefinitionBuilderFactory elementBeanDefinitionBuilderFactory) {
                ClassElement newParent = parentType.withAnnotationMetadata(parentType.copyAnnotationMetadata()); // Just a copy
                return elementBeanDefinitionBuilderFactory.factoryField(
                    BeanInjectionUtils.createFieldDefinition(
                        newParent,
                        producerField.withAnnotationMetadata(
                            new AnnotationMetadataHierarchy(newParent.getDeclaredMetadata(), producerField.getDeclaredMetadata())
                        ),
                        !producerField.isPublic(),
                        visitorContext
                    )
                );
            }

        };
    }

    @Override
    protected @NonNull AbstractBeanDefinitionBuilder createChildBean(MethodElement producerMethod) {
        final ClassElement parentType = getBeanType();
        return new GroovyBeanDefinitionBuilder(
            GroovyBeanDefinitionBuilder.this.getOriginatingElement(),
            producerMethod.getGenericReturnType().getType(),
            elementAnnotationMetadataFactory,
            GroovyBeanDefinitionBuilder.this.visitorContext
        ) {
            BeanParameterElement[] parameters;

            @Override
            public @NonNull Element getProducingElement() {
                return producerMethod;
            }

            @Override
            public @NonNull ClassElement getDeclaringElement() {
                return producerMethod.getDeclaringType();
            }

            @Override
            protected BeanParameterElement[] getParameters() {
                if (parameters == null) {
                    parameters = initBeanParameters(producerMethod.getParameters());
                }
                return parameters;
            }

            @Override
            protected ElementBeanDefinitionBuilder createBeanDefinitionBuilder(ElementBeanDefinitionBuilderFactory elementBeanDefinitionBuilderFactory) {
                final GroovyElementFactory elementFactory = ((GroovyVisitorContext) visitorContext).getElementFactory();
                ClassElement resolvedParent = resolveParent(parentType, elementFactory);
                AnnotationMetadataHierarchy annotationMetadata = new AnnotationMetadataHierarchy(resolvedParent.getDeclaredMetadata(), producerMethod.getDeclaredMetadata(), getAnnotationMetadata());
                return elementBeanDefinitionBuilderFactory.factoryMethod(
                    BeanInjectionUtils.createMethodDefinition(
                        resolvedParent,
                        producerMethod.withParameters(getParameters()).withAnnotationMetadata(annotationMetadata),
                        annotationMetadata,
                        !producerMethod.isPublic(),
                        visitorContext
                    ));
            }

        };
    }

    @Override
    protected <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        if (consumer != null && annotationMetadata != null && annotationType != null) {
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
            consumer.accept(builder);
            AnnotationValue<T> av = builder.build();
            annotationBuilder.annotate(annotationMetadata, av);
        }
    }

    @Override
    protected <T extends Annotation> void annotate(@NonNull AnnotationMetadata annotationMetadata, @NonNull AnnotationValue<T> annotationValue) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("annotationValue", annotationValue);
        annotationBuilder.annotate(annotationMetadata, annotationValue);
    }

    @Override
    protected void removeStereotype(AnnotationMetadata annotationMetadata, String annotationType) {
        if (annotationMetadata != null && annotationType != null) {
            annotationBuilder.removeStereotype(annotationMetadata, annotationType);
        }
    }

    @Override
    protected <T extends Annotation> void removeAnnotationIf(AnnotationMetadata annotationMetadata, Predicate<AnnotationValue<T>> predicate) {
        if (annotationMetadata != null && predicate != null) {
            annotationBuilder.removeAnnotationIf(annotationMetadata, predicate);
        }
    }

    @Override
    protected void removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType) {
        if (annotationMetadata != null && annotationType != null) {
            annotationBuilder.removeAnnotation(annotationMetadata, annotationType);
        }
    }

    private ClassElement resolveParent(ClassElement parentType, GroovyElementFactory elementFactory) {
        ClassElement resolvedParent = parentType;
        if (parentType instanceof GroovyClassElement groovyClassElement) {
            resolvedParent = elementFactory.newClassElement(groovyClassElement.classNode, elementAnnotationMetadataFactory);
        }
        return resolvedParent;
    }

}
