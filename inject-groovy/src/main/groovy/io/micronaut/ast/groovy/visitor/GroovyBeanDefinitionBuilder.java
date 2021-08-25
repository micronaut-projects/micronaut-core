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
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

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

    /**
     * Default constructor.
     *
     * @param originatingElement The originating element
     * @param beanType           The bean type
     * @param metadataBuilder    the metadata builder
     * @param visitorContext     the visitor context
     */
    GroovyBeanDefinitionBuilder(
            Element originatingElement,
            ClassElement beanType,
            ConfigurationMetadataBuilder<?> metadataBuilder,
            GroovyVisitorContext visitorContext) {
        super(originatingElement, beanType, metadataBuilder, visitorContext);
        if (getClass() == GroovyBeanDefinitionBuilder.class) {
            visitorContext.addBeanDefinitionBuilder(this);
        }
        this.visitorContext = visitorContext;
    }

    @Override
    protected AbstractBeanDefinitionBuilder createChildBean(FieldElement producerField) {
        final ClassElement parentType = getBeanType();
        return new GroovyBeanDefinitionBuilder(
                GroovyBeanDefinitionBuilder.this.getOriginatingElement(),
                producerField.getGenericField().getType(),
                GroovyBeanDefinitionBuilder.this.metadataBuilder,
                GroovyBeanDefinitionBuilder.this.visitorContext
        ) {
            @Override
            public Element getProducingElement() {
                return producerField;
            }

            @Override
            public ClassElement getDeclaringElement() {
                return producerField.getDeclaringType();
            }

            @Override
            protected BeanDefinitionWriter createBeanDefinitionWriter() {
                final BeanDefinitionWriter writer = super.createBeanDefinitionWriter();
                final GroovyElementFactory elementFactory = ((GroovyVisitorContext) visitorContext).getElementFactory();
                final FieldNode fieldNode = (FieldNode) producerField.getNativeType();
                writer.visitBeanFactoryField(
                        parentType,
                        elementFactory.newFieldElement(
                                parentType,
                                fieldNode,
                                new AnnotationMetadataHierarchy(parentType.getDeclaredMetadata(), producerField.getDeclaredMetadata())
                        )
                );
                return writer;
            }
        };
    }

    @Override
    protected AbstractBeanDefinitionBuilder createChildBean(MethodElement producerMethod) {
        final ClassElement parentType = getBeanType();
        return new GroovyBeanDefinitionBuilder(
                GroovyBeanDefinitionBuilder.this.getOriginatingElement(),
                producerMethod.getGenericReturnType().getType(),
                GroovyBeanDefinitionBuilder.this.metadataBuilder,
                GroovyBeanDefinitionBuilder.this.visitorContext
        ) {
            @Override
            public Element getProducingElement() {
                return producerMethod;
            }

            @Override
            public ClassElement getDeclaringElement() {
                return producerMethod.getDeclaringType();
            }

            @Override
            protected BeanDefinitionWriter createBeanDefinitionWriter() {
                final BeanDefinitionWriter writer = super.createBeanDefinitionWriter();
                final GroovyElementFactory elementFactory = ((GroovyVisitorContext) visitorContext).getElementFactory();
                final MethodNode methodNode = (MethodNode) producerMethod.getNativeType();
                writer.visitBeanFactoryMethod(
                        parentType,
                        elementFactory.newMethodElement(
                                parentType,
                                methodNode,
                                new AnnotationMetadataHierarchy(parentType.getDeclaredMetadata(), producerMethod.getDeclaredMetadata())
                        )
                );
                return writer;
            }
        };
    }

    @Override
    protected <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        if (consumer != null && annotationMetadata != null && annotationType != null) {
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
            consumer.accept(builder);
            AnnotationValue<T> av = builder.build();
            final GroovyAnnotationMetadataBuilder annotationBuilder = new GroovyAnnotationMetadataBuilder(
                    visitorContext.getSourceUnit(),
                    visitorContext.getCompilationUnit());
            annotationBuilder.annotate(
                    annotationMetadata,
                    av
            );
        }
    }

    @Override
    protected void removeStereotype(AnnotationMetadata annotationMetadata, String annotationType) {
        if (annotationMetadata != null && annotationType != null) {
            final GroovyAnnotationMetadataBuilder annotationBuilder = new GroovyAnnotationMetadataBuilder(
                    visitorContext.getSourceUnit(),
                    visitorContext.getCompilationUnit());
            annotationBuilder.removeStereotype(
                    annotationMetadata,
                    annotationType
            );
        }
    }

    @Override
    protected <T extends Annotation> void removeAnnotationIf(AnnotationMetadata annotationMetadata, Predicate<AnnotationValue<T>> predicate) {
        if (annotationMetadata != null && predicate != null) {
            final GroovyAnnotationMetadataBuilder annotationBuilder = new GroovyAnnotationMetadataBuilder(
                    visitorContext.getSourceUnit(),
                    visitorContext.getCompilationUnit());
            annotationBuilder.removeAnnotationIf(
                    annotationMetadata,
                    predicate
            );
        }
    }

    @Override
    protected void removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType) {
        if (annotationMetadata != null && annotationType != null) {
            final GroovyAnnotationMetadataBuilder annotationBuilder = new GroovyAnnotationMetadataBuilder(
                    visitorContext.getSourceUnit(),
                    visitorContext.getCompilationUnit());
            annotationBuilder.removeAnnotation(
                    annotationMetadata,
                    annotationType
            );
        }
    }
}
