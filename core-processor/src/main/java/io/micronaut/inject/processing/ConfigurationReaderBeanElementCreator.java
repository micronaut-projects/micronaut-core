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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
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
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Configuration reader bean builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class ConfigurationReaderBeanElementCreator extends DeclaredBeanElementCreator {

    ConfigurationReaderBeanElementCreator(ClassElement classElement, VisitorContext visitorContext) {
        super(classElement, visitorContext, false);
    }

    @Override
    protected void applyConfigurationInjectionIfNecessary(BeanDefinitionVisitor visitor,
                                                          MethodElement constructor) {
        if (!classElement.isRecord() && !constructor.hasAnnotation(ConfigurationInject.class)) {
            return;
        }
        if (constructor.hasStereotype(ANN_REQUIRES_VALIDATION)) {
            visitor.setValidated(true);
        }
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
    protected boolean visitProperty(BeanDefinitionVisitor visitor, PropertyElement propertyElement) {
        Optional<MethodElement> readMethod = propertyElement.getReadMethod();
        Optional<FieldElement> field = propertyElement.getField();
        if (propertyElement.hasStereotype(ConfigurationBuilder.class)) {
            // Exclude / ignore shouldn't affect builders
            if (readMethod.isPresent()) {
                MethodElement methodElement = readMethod.get();
                visitor.visitConfigBuilder(
                    ConfigurationBuilderDefinition.of(classElement, methodElement.withAnnotationMetadata(propertyElement.getAnnotationMetadata()), visitorContext)
                );
                return true;
            }
            if (field.isPresent()) {
                FieldElement fieldElement = field.get();
                if (fieldElement.isAccessible(classElement)) {
                    visitor.visitConfigBuilder(
                        ConfigurationBuilderDefinition.of(classElement, fieldElement, visitorContext)
                    );
                    return true;
                }
                throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
            }
        } else if (!propertyElement.isExcluded()) {
            boolean claimed = false;
            Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
            if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.METHOD && writeMethod.isPresent()) {
                visitor.setValidated(visitor.isValidated() || propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION));
                MethodElement methodElement = writeMethod.get();
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
            } else if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.FIELD && field.isPresent()) {
                visitor.setValidated(visitor.isValidated() || propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION));
                FieldElement fieldElement = field.get();
                AnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(propertyElement.getAnnotationMetadata());
                annotationMetadata = calculatePath(propertyElement, fieldElement, annotationMetadata);
                visitor.visitFieldValue(fieldElement.getDeclaringType(), fieldElement.withAnnotationMetadata(annotationMetadata), fieldElement.isReflectionRequired(classElement), true);
                claimed = true;
            }
            if (readMethod.isPresent()) {
                MethodElement methodElement = readMethod.get();
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
        if (fieldElement.hasStereotype(ConfigurationBuilder.class)) {
            if (fieldElement.isAccessible(classElement)) {
                visitor.visitConfigBuilder(
                    ConfigurationBuilderDefinition.of(classElement, fieldElement, visitorContext)
                );
                return true;
            }
            throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
        }
        return super.visitField(visitor, fieldElement);
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

}
