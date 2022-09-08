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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.BeanPropertiesConfiguration;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Configuration reader bean builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
final class ConfigurationReaderBeanDefinitionBuilder extends DeclaredBeanDefinitionBuilder {

    private static final List<String> CONSTRUCTOR_PARAMETERS_INJECTION_ANN =
        Arrays.asList(Property.class.getName(), Value.class.getName(), Parameter.class.getName(), AnnotationUtil.QUALIFIER, AnnotationUtil.INJECT);

    private final ConfigurationMetadataBuilder metadataBuilder = ConfigurationMetadataBuilder.INSTANCE;

    ConfigurationReaderBeanDefinitionBuilder(ClassElement classElement, VisitorContext visitorContext) {
        super(classElement, visitorContext, false);
    }

    @Override
    protected void applyConfigurationInjectionIfNecessary(BeanDefinitionVisitor visitor,
                                                          MethodElement constructor) {
        if (!classElement.isRecord() && !constructor.hasAnnotation(ConfigurationInject.class)) {
            return;
        }
        if (classElement.isRecord()) {
            final List<PropertyElement> beanProperties = constructor
                .getDeclaringType()
                .getBeanProperties();
            final ParameterElement[] parameters = constructor.getParameters();
            if (beanProperties.size() == parameters.length) {
                for (int i = 0; i < parameters.length; i++) {
                    ParameterElement parameter = parameters[i];
                    final PropertyElement bp = beanProperties.get(i);
                    if (CONSTRUCTOR_PARAMETERS_INJECTION_ANN.stream().noneMatch(bp::hasStereotype)) {
                        processConfigurationConstructorParameter(parameter);
                    }
                }
                if (constructor.hasStereotype(ANN_REQUIRES_VALIDATION)) {
                    visitor.setValidated(true);
                }
                return;
            }
        }
        processConfigurationInjectionConstructor(visitor, constructor);
    }

    private void processConfigurationInjectionConstructor(BeanDefinitionVisitor visitor,
                                                          MethodElement constructor) {
        for (ParameterElement parameter : constructor.getParameters()) {
            if (CONSTRUCTOR_PARAMETERS_INJECTION_ANN.stream().noneMatch(parameter::hasStereotype)) {
                processConfigurationConstructorParameter(parameter);
            }
        }
        if (constructor.hasStereotype(ANN_REQUIRES_VALIDATION)) {
            visitor.setValidated(true);
        }
    }

    private void processConfigurationConstructorParameter(ParameterElement parameter) {
        if (!parameter.hasStereotype(AnnotationUtil.SCOPE)) {
            final PropertyMetadata pm = metadataBuilder.visitProperty(
                parameter.getMethodElement().getOwningType(),
                parameter.getMethodElement().getDeclaringType(),
                parameter.getGenericType(),
                parameter.getName(), parameter.getDocumentation().orElse(null),
                parameter.stringValue(Bindable.class, "defaultValue").orElse(null)
            );
            parameter.annotate(Property.class, (builder) -> builder.member("name", pm.getPath()));
        }
    }

    public static boolean isConfigurationProperties(ClassElement classElement) {
        return classElement.hasStereotype(ConfigurationReader.class);
    }

    @Override
    protected boolean visitAopMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        return false;
    }

    @Override
    protected boolean processAsProperties() {
        return true;
    }

    @Override
    protected boolean visitProperty(BeanDefinitionVisitor visitor, PropertyElement propertyElement) {
        if (propertyElement.hasStereotype(ConfigurationBuilder.class)) {
            // Exclude / ignore shouldn't affect builders
            if (propertyElement.getReadMethod().isPresent()) {
                MethodElement methodElement = propertyElement.getReadMethod().get();
                ClassElement builderType = methodElement.getReturnType();
                visitor.visitConfigBuilderMethod(
                    builderType,
                    methodElement.getName(),
                    propertyElement.getAnnotationMetadata(),
                    null,
                    builderType.isInterface()
                );
                visitConfigurationBuilder(visitor, propertyElement, builderType);
                return true;
            }
            if (propertyElement.getField().isPresent()) {
                FieldElement fieldElement = propertyElement.getField().get();
                if (fieldElement.isAccessible(classElement)) {
                    ClassElement builderType = fieldElement.getType();
                    visitor.visitConfigBuilderField(
                        builderType,
                        fieldElement.getName(),
                        fieldElement.getAnnotationMetadata(),
                        metadataBuilder,
                        builderType.isInterface()
                    );
                    visitConfigurationBuilder(visitor, propertyElement, builderType);
                    return true;
                }
                throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
            }
        } else if (!propertyElement.isExcluded()) {
            boolean claimed = false;
            if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.METHOD && propertyElement.getWriteMethod().isPresent()) {
                visitor.setValidated(visitor.isValidated() || propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION));
                MethodElement methodElement = propertyElement.getWriteMethod().get();
                ParameterElement parameter = methodElement.getParameters()[0];
                AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                    propertyElement,
                    parameter
                ).merge();
                annotationMetadata = calculatePath(propertyElement, methodElement, annotationMetadata);
                AnnotationMetadata finalAnnotationMetadata = annotationMetadata;
                methodElement = methodElement
                    .withAnnotationMetadata(annotationMetadata)
                    .withParameters(
                        Arrays.stream(methodElement.getParameters())
                            .map(p -> p == parameter ? parameter.withAnnotationMetadata(finalAnnotationMetadata) : p)
                            .toArray(ParameterElement[]::new)
                    );
                visitor.visitSetterValue(methodElement.getDeclaringType(), methodElement, annotationMetadata, methodElement.isReflectionRequired(classElement), true);
                claimed = true;
            } else if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.FIELD && propertyElement.getField().isPresent()) {
                visitor.setValidated(visitor.isValidated() || propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION));
                FieldElement fieldElement = propertyElement.getField().get();
                AnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(propertyElement.getAnnotationMetadata());
                annotationMetadata = calculatePath(propertyElement, fieldElement, annotationMetadata);
                visitor.visitFieldValue(fieldElement.getDeclaringType(), fieldElement.withAnnotationMetadata(annotationMetadata), true, fieldElement.isReflectionRequired(classElement));
                claimed = true;
            }
            if (propertyElement.getReadMethod().isPresent()) {
                MethodElement methodElement = propertyElement.getReadMethod().get();
                if (methodElement.hasStereotype(Executable.class)) {
                    claimed |= visitExecutableMethod(visitor, methodElement);
                }
            }
            return claimed;
        }
        return false;
    }

    @Override
    protected boolean visitField(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        if (fieldElement.hasStereotype(ConfigurationBuilder.class) && !fieldElement.isAccessible(classElement)) {
            throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
        }
        return super.visitField(visitor, fieldElement);
    }

    private AnnotationMetadata calculatePath(PropertyElement propertyElement, MemberElement writeMember, AnnotationMetadata annotationMetadata) {
        String path = metadataBuilder.visitProperty(
            writeMember.getOwningType(),
            writeMember.getDeclaringType(),
            propertyElement.getGenericType(),
            propertyElement.getName(),
            propertyElement.getDocumentation().orElse(null),
            null
        ).getPath();
        return visitorContext.getAnnotationMetadataBuilder().annotate(annotationMetadata, AnnotationValue.builder(Property.class).member("name", path).build());
    }

    @Override
    protected boolean isInjectPointMethod(MemberElement memberElement) {
        return super.isInjectPointMethod(memberElement) || memberElement.hasDeclaredStereotype(ConfigurationInject.class);
    }

    private void visitConfigurationBuilder(BeanDefinitionVisitor visitor,
                                           MemberElement builderElement,
                                           ClassElement builderType) {
        try {
            String configurationPrefix = builderElement.stringValue(ConfigurationBuilder.class).map(v -> v + ".").orElse("");
            builderType.getBeanProperties(BeanPropertiesConfiguration.of(builderElement))
                .stream()
                .filter(propertyElement -> {
                    if (propertyElement.isExcluded()) {
                        return false;
                    }
                    Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
                    if (!writeMethod.isPresent()) {
                        return false;
                    }
                    MethodElement methodElement = writeMethod.get();
                    if (methodElement.hasStereotype(Deprecated.class) || !methodElement.isPublic()) {
                        return false;
                    }
                    return methodElement.getParameters().length <= 2;
                }).forEach(propertyElement -> {
                    MethodElement methodElement = propertyElement.getWriteMethod().get();
                    String propertyName = propertyElement.getName();
                    ParameterElement[] params = methodElement.getParameters();
                    int paramCount = params.length;
                    if (paramCount < 2) {
                        ParameterElement parameterElement = paramCount == 1 ? params[0] : null;
                        ClassElement parameterElementType = parameterElement == null ? null : parameterElement.getGenericType();

                        PropertyMetadata metadata = metadataBuilder.visitProperty(
                            classElement,
                            builderElement.getDeclaringType(),
                            methodElement.getReturnType().getGenericType(),
                            configurationPrefix + propertyName,
                            null,
                            null
                        );

                        visitor.visitConfigBuilderMethod(
                            propertyName,
                            methodElement.getReturnType(),
                            methodElement.getSimpleName(),
                            parameterElementType,
                            parameterElementType != null ? parameterElementType.getTypeArguments() : null,
                            metadata.getPath()
                        );
                    } else if (paramCount == 2) {
                        // check the params are a long and a TimeUnit
                        ParameterElement first = params[0];
                        ParameterElement second = params[1];
                        ClassElement firstParamType = first.getType();
                        ClassElement secondParamType = second.getType();

                        if (firstParamType.getSimpleName().equals("long") && secondParamType.isAssignable(TimeUnit.class)) {
                            PropertyMetadata metadata = metadataBuilder.visitProperty(
                                classElement,
                                methodElement.getDeclaringType(),
                                visitorContext.getClassElement(Duration.class.getName()).get(),
                                configurationPrefix + propertyName,
                                null,
                                null
                            );

                            visitor.visitConfigBuilderDurationMethod(
                                propertyName,
                                methodElement.getReturnType(),
                                methodElement.getSimpleName(),
                                metadata.getPath()
                            );
                        }
                    }
                });
        } finally {
            visitor.visitConfigBuilderEnd();
        }
    }

}
