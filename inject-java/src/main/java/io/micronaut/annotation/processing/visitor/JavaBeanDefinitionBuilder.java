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

import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Java implementation of {@link AbstractBeanDefinitionBuilder}.
 *
 * @author graemerocher
 * @since 3.0.0
 */
final class JavaBeanDefinitionBuilder extends AbstractBeanDefinitionBuilder {
    private final JavaVisitorContext javaVisitorContext;

    /**
     * Default constructor.
     *
     * @param originatingElement The originating element
     * @param beanType           The bean type
     * @param metadataBuilder    the metadata builder
     * @param visitorContext     the visitor context
     */
    JavaBeanDefinitionBuilder(Element originatingElement, ClassElement beanType, ConfigurationMetadataBuilder<?> metadataBuilder, JavaVisitorContext visitorContext) {
        super(originatingElement, beanType, metadataBuilder, visitorContext);
        this.javaVisitorContext = visitorContext;
        if (visitorContext.getVisitorKind() == TypeElementVisitor.VisitorKind.ISOLATING) {
            visitorContext.addBeanDefinitionBuilder(this);
        } else {
            visitorContext.fail("Cannot add bean definition using addAssociatedBean(..) from a AGGREGATING TypeElementVisitor, consider overriding getVisitorKind()", originatingElement);
        }
    }

    @Override
    protected <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        ArgumentUtils.requireNonNull("consumer", consumer);

        final AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        final AnnotationValue<T> av = builder.build();
        AnnotationUtils annotationUtils = javaVisitorContext
                .getAnnotationUtils();
        annotationUtils
                .newAnnotationBuilder()
                .annotate(annotationMetadata, av);
    }

    @Override
    protected void removeStereotype(AnnotationMetadata annotationMetadata, String annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        AnnotationUtils annotationUtils = javaVisitorContext
                .getAnnotationUtils();
        annotationUtils
                .newAnnotationBuilder()
                .removeStereotype(annotationMetadata, annotationType);
    }

    @Override
    protected <T extends Annotation> void removeAnnotationIf(AnnotationMetadata annotationMetadata, Predicate<AnnotationValue<T>> predicate) {
        ArgumentUtils.requireNonNull("predicate", predicate);
        AnnotationUtils annotationUtils = javaVisitorContext
                .getAnnotationUtils();
        annotationUtils
                .newAnnotationBuilder()
                .removeAnnotationIf(annotationMetadata, predicate);
    }

    @Override
    protected void removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        AnnotationUtils annotationUtils = javaVisitorContext
                .getAnnotationUtils();
        annotationUtils
                .newAnnotationBuilder()
                .removeAnnotation(annotationMetadata, annotationType);
    }
}
