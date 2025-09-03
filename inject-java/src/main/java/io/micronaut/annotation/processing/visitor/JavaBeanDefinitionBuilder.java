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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder;
import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.beans.BeanParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import java.lang.annotation.Annotation;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Java implementation of {@link AbstractBeanDefinitionBuilder}.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
class JavaBeanDefinitionBuilder extends AbstractBeanDefinitionBuilder {
    private final JavaAnnotationMetadataBuilder annotationMetadataBuilder;

    /**
     * Default constructor.
     *
     * @param originatingElement The originating element
     * @param beanType The bean type
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @param visitorContext the visitor context
     */
    JavaBeanDefinitionBuilder(Element originatingElement,
                              ClassElement beanType,
                              ElementAnnotationMetadataFactory elementAnnotationMetadataFactory,
                              JavaVisitorContext visitorContext) {
        super(originatingElement, beanType, visitorContext, elementAnnotationMetadataFactory);
        if (visitorContext.getVisitorKind() == TypeElementVisitor.VisitorKind.ISOLATING) {
            if (getClass() == JavaBeanDefinitionBuilder.class) {
                visitorContext.addBeanDefinitionBuilder(this);
            }
        } else {
            visitorContext.fail("Cannot add bean definition using addAssociatedBean(..) from a AGGREGATING TypeElementVisitor, consider overriding getVisitorKind()", originatingElement);
        }
        this.annotationMetadataBuilder = visitorContext.getAnnotationMetadataBuilder();
    }

    @NonNull
    @Override
    protected AbstractBeanDefinitionBuilder createChildBean(FieldElement producerField) {
        final ClassElement parentType = getBeanType();
        return new JavaBeanDefinitionBuilder(
            JavaBeanDefinitionBuilder.this.getOriginatingElement(),
            producerField.getGenericField().getType(),
            elementAnnotationMetadataFactory,
            (JavaVisitorContext) JavaBeanDefinitionBuilder.this.visitorContext
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
            protected BeanDefinitionVisitor createBeanDefinitionWriter() {
                final BeanDefinitionVisitor writer = super.createBeanDefinitionWriter();
                ClassElement newParent = parentType.withAnnotationMetadata(parentType.copyAnnotationMetadata()); // Just a copy
                writer.visitBeanFactoryField(
                    newParent,
                    producerField.withAnnotationMetadata(
                        new AnnotationMetadataHierarchy(newParent.getDeclaredMetadata(), producerField.getDeclaredMetadata())
                    )
                );
                return writer;
            }
        };
    }

    @NonNull
    @Override
    protected BeanDefinitionVisitor createAopWriter(BeanDefinitionWriter beanDefinitionWriter, AnnotationMetadata annotationMetadata) {
        AnnotationValue<?>[] interceptorTypes =
            InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND);
        return new AopProxyWriter(
            beanDefinitionWriter,
            annotationMetadata.getValues(Around.class, Boolean.class),
            visitorContext,
            interceptorTypes
        );
    }

    @NonNull
    @Override
    protected BiConsumer<TypedElement, MethodElement> createAroundMethodVisitor(BeanDefinitionVisitor aopWriter) {
        AopProxyWriter aopProxyWriter = (AopProxyWriter) aopWriter;
        return (bean, method) -> {
            AnnotationValue<?>[] newTypes =
                InterceptedMethodUtil.resolveInterceptorBinding(method.getAnnotationMetadata(), InterceptorKind.AROUND);
            aopProxyWriter.visitInterceptorBinding(newTypes);
            aopProxyWriter.visitAroundMethod(
                bean, method
            );
        };
    }

    @NonNull
    @Override
    protected AbstractBeanDefinitionBuilder createChildBean(MethodElement producerMethod) {
        final ClassElement parentType = getBeanType();
        return new JavaBeanDefinitionBuilder(
            JavaBeanDefinitionBuilder.this.getOriginatingElement(),
            producerMethod.getGenericReturnType(),
            elementAnnotationMetadataFactory,
            (JavaVisitorContext) JavaBeanDefinitionBuilder.this.visitorContext
        ) {
            BeanParameterElement[] parameters;

            @NonNull
            @Override
            public Element getProducingElement() {
                return producerMethod;
            }

            @NonNull
            @Override
            public ClassElement getDeclaringElement() {
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
            protected BeanDefinitionVisitor createBeanDefinitionWriter() {
                final BeanDefinitionVisitor writer = super.createBeanDefinitionWriter();
                ClassElement newParent = parentType.withAnnotationMetadata(parentType.copyAnnotationMetadata()); // Just a copy
                writer.visitBeanFactoryMethod(
                    newParent,
                    producerMethod.withAnnotationMetadata(
                        new AnnotationMetadataHierarchy(newParent.getDeclaredMetadata(), producerMethod.getDeclaredMetadata())
                    ),
                    getParameters()
                );
                return writer;
            }

        };
    }

    @Override
    protected <T extends Annotation> void annotate(@NonNull AnnotationMetadata annotationMetadata, @NonNull AnnotationValue<T> annotationValue) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("annotationValue", annotationValue);
        annotationMetadataBuilder.annotate(annotationMetadata, annotationValue);
    }

    @Override
    protected <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        ArgumentUtils.requireNonNull("consumer", consumer);
        final AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        final AnnotationValue<T> av = builder.build();
        annotationMetadataBuilder.annotate(annotationMetadata, av);
    }

    @Override
    protected void removeStereotype(AnnotationMetadata annotationMetadata, String annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        annotationMetadataBuilder.removeStereotype(annotationMetadata, annotationType);
    }

    @Override
    protected <T extends Annotation> void removeAnnotationIf(AnnotationMetadata annotationMetadata, Predicate<AnnotationValue<T>> predicate) {
        ArgumentUtils.requireNonNull("predicate", predicate);
        annotationMetadataBuilder.removeAnnotationIf(annotationMetadata, predicate);
    }

    @Override
    protected void removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        annotationMetadataBuilder.removeAnnotation(annotationMetadata, annotationType);
    }

}
