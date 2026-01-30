/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.processing;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.PropertyInjectionPoint;
import io.micronaut.context.bean.definition.builder.MethodDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderDefinition;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderOfFieldDefinition;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderOfPropertyDefinition;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderPropertyDefinition;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Configuration reader bean builder.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class ConfigurationReaderBeanElementCreator<R> extends DeclaredBeanElementCreator<R> {

    ConfigurationReaderBeanElementCreator(ClassElement classElement, VisitorContext visitorContext, ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilder) {
        super(classElement, visitorContext, false, beanDefinitionBuilder);
    }

    public static boolean isConfigurationProperties(ClassElement classElement) {
        return classElement.hasStereotype(ConfigurationReader.class);
    }

    @Override
    protected void makeInterceptedForValidationIfNeeded(MethodElement element) {
        // Configuration beans are validated by the introspection
    }

    @Override
    protected boolean processAsProperties() {
        return true;
    }

    @Override
    protected boolean visitProperty(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement) {
        Optional<MethodElement> readMethod = propertyElement.getReadMethod();
        Optional<FieldElement> field = propertyElement.getField();
        if (propertyElement.hasStereotype(ConfigurationBuilder.class)) {
            // Exclude / ignore shouldn't affect builders
            if (readMethod.isPresent()) {
                addConfigBuilder(
                    beanDefinitionBuilder,
                    ConfigurationBuilderDefinition.of(classElement, propertyElement, visitorContext)
                );
                return true;
            }
            if (field.isPresent()) {
                FieldElement fieldElement = field.get();
                if (fieldElement.isAccessible(classElement)) {
                    addConfigBuilder(
                        beanDefinitionBuilder,
                        ConfigurationBuilderDefinition.of(classElement, propertyElement, visitorContext)
                    );
                    return true;
                }
                throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
            }
        } else if (!propertyElement.isExcluded()) {

            boolean claimed = false;
            Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
            if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.METHOD && writeMethod.isPresent()) {
                MethodElement methodElement = writeMethod.get();
                ParameterElement[] parameters = methodElement.getParameters();
                if (parameters.length != 1) {
                    throw new IllegalStateException("Setter method [" + methodElement.getName() + "] must have exactly one parameter");
                }
                ParameterElement parameter = methodElement.getParameters()[0];
                AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                    propertyElement,
                    parameter
                ).merge();

                // TODO: Avoid modifying the annotation metadata, simply generate the path and pass it to the create injection point method
                annotationMetadata = calculatePath(propertyElement, methodElement, annotationMetadata);
                AnnotationMetadata finalAnnotationMetadata = annotationMetadata;
                methodElement = methodElement
                    .withAnnotationMetadata(annotationMetadata)
                    .withParameters(
                        Arrays.stream(methodElement.getParameters())
                            .map(p -> p == parameter ? parameter.withAnnotationMetadata(finalAnnotationMetadata) : p)
                            .toArray(ParameterElement[]::new)
                    );

                boolean reflectionRequired = methodElement.isReflectionRequired(classElement);
                BeanDefinitionInjectionPoint<ClassElement> injectionPoint = BeanInjectionUtils.createValueInjectionPoint(
                    methodElement.getOwningType(),
                    parameter.getGenericType(),
                    annotationMetadata,
                    reflectionRequired,
                    parameter.getName(),
                    visitorContext
                );
                beanDefinitionBuilder.addMethodInjection(
                    new MethodDefinition<>(
                        methodElement,
                        annotationMetadata,
                        List.of(injectionPoint),
                        reflectionRequired,
                        true,
                        true,
                        null)
                );
                claimed = true;
            } else if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.FIELD && field.isPresent()) {
                FieldElement fieldElement = field.get();
                AnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(propertyElement.getAnnotationMetadata());
                // TODO: Avoid modifying the annotation metadata, simply generate the path and pass it to the create injection point method
                annotationMetadata = calculatePath(propertyElement, fieldElement, annotationMetadata);

                fieldElement = fieldElement.withAnnotationMetadata(annotationMetadata);

                beanDefinitionBuilder.addFieldPropertyInjection(fieldElement, annotationMetadata, fieldElement.isReflectionRequired(classElement), true, visitorContext);

                claimed = true;
            }
            if (readMethod.isPresent()) {
                MethodElement methodElement = readMethod.get();
                if (methodElement.hasStereotype(Executable.class)) {
                    claimed |= visitExecutableMethod(beanDefinitionBuilder, methodElement);
                }
            }
            return claimed;
        }
        return false;
    }

    @Override
    protected boolean visitField(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, FieldElement fieldElement) {
        if (fieldElement.hasStereotype(ConfigurationBuilder.class)) {
            if (fieldElement.isAccessible(classElement)) {
                addConfigBuilder(
                    beanDefinitionBuilder,
                    ConfigurationBuilderDefinition.of(classElement, fieldElement, visitorContext)
                );
                return true;
            }
            throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
        }
        return super.visitField(beanDefinitionBuilder, fieldElement);
    }

    private AnnotationMetadata calculatePath(PropertyElement propertyElement, MemberElement writeMember, AnnotationMetadata annotationMetadata) {
        String path = ConfigurationMetadataBuilder.calculatePath(
            writeMember.getOwningType(),
            writeMember.getDeclaringType(),
            propertyElement.getGenericType(),
            propertyElement.getName()
        );
        return visitorContext.getAnnotationMetadataBuilder().annotate(annotationMetadata, AnnotationValue.builder(Property.class).member("name", path).build());
    }

    @Override
    protected boolean isInjectPointMethod(MemberElement memberElement) {
        return super.isInjectPointMethod(memberElement) || memberElement.hasDeclaredStereotype(ConfigurationInject.class);
    }

    private void addConfigBuilder(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, ConfigurationBuilderDefinition builderDefinition) {
        switch (builderDefinition) {
            case ConfigurationBuilderOfFieldDefinition conf -> {
                beanDefinitionBuilder.addFieldConfigurationBuilder(
                    conf.fieldElement(),
                    conf.fieldElement(),
                    convertBuilderMethods(conf.elements())
                );
            }
            case ConfigurationBuilderOfPropertyDefinition conf -> {
                PropertyElement property = conf.property();
                Optional<? extends MemberElement> readMember = property.getReadMember();
                if (readMember.isPresent()) {
                    MemberElement memberElement = readMember.get();
                    if (memberElement instanceof MethodElement method) {
                        beanDefinitionBuilder.addMethodConfigurationBuilder(
                            method,
                            property,
                            convertBuilderMethods(conf.elements())
                        );
                    }
                    if (memberElement instanceof FieldElement field) {
                        beanDefinitionBuilder.addFieldConfigurationBuilder(
                            field,
                            property,
                            convertBuilderMethods(conf.elements())
                        );
                    }
                }
            }
        }
    }

    private List<MethodDefinition<ClassElement, MethodElement>> convertBuilderMethods(List<ConfigurationBuilderPropertyDefinition> elements) {
        return elements.stream().map(e -> {
            List<BeanDefinitionInjectionPoint<ClassElement>> injectionPoints = new ArrayList<>();
            BeanDefinitionInjectionPoint.PropertyInjectionPoint<ClassElement> booleanInjectionPoint = null;
            MethodElement method = e.method();
            if (method.getParameters().length == 0) {
                booleanInjectionPoint = new PropertyInjectionPoint<>(e.type(), e.method(), e.name(), e.path());
            } else {
                injectionPoints.add(new PropertyInjectionPoint<>(e.type(), method.getParameters()[0], e.name(), e.path()));
            }
            return new MethodDefinition<>(method, method, injectionPoints, false, false, false, booleanInjectionPoint);
        }).toList();
    }

}
