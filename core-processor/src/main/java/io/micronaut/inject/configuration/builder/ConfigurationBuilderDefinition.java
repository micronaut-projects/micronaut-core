/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.configuration.builder;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.VisitorContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for building configuration elements using builder definitions.
 * <p>
 * The class provides methods to facilitate the creation of configuration
 * metadata for builder patterns and processes their associated methods
 * and properties.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public interface ConfigurationBuilderDefinition {

    static ConfigurationBuilderDefinition of(ClassElement owningType,
                                             FieldElement fieldElement,
                                             VisitorContext visitorContext) {
        return new ConfigurationBuilderOfFieldDefinition(fieldElement, provide(owningType, fieldElement, fieldElement.getType(), visitorContext));
    }

    static ConfigurationBuilderDefinition of(ClassElement owningType,
                                             MethodElement methodElement,
                                             VisitorContext visitorContext) {
        return new ConfigurationBuilderOfMethodDefinition(methodElement, provide(owningType, methodElement, methodElement.getReturnType(), visitorContext));
    }

    /**
     * Provide the builder definition.
     *
     * @param owningType   The class element
     * @param builderElement The builder element
     * @param builderType    The builder type
     * @param visitorContext The visitor context
     * @return The list of elements
     */
    private static List<ConfigurationBuilderPropertyDefinition> provide(ClassElement owningType,
                                                                       MemberElement builderElement,
                                                                       ClassElement builderType,
                                                                       VisitorContext visitorContext) {
        List<ConfigurationBuilderPropertyDefinition> builderElementDefinitions = new ArrayList<>();
        AnnotationMetadata annotationMetadata = builderElement.getAnnotationMetadata();
        String configurationPrefix = annotationMetadata.stringValue(ConfigurationBuilder.class).map(v -> v + ".").orElse("");
        builderType.getBeanProperties(PropertyElementQuery.of(annotationMetadata))
            .stream()
            .filter(propertyElement -> {
                if (propertyElement.isExcluded()) {
                    return false;
                }
                Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
                if (writeMethod.isEmpty()) {
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

                    String path = ConfigurationMetadataBuilder.calculatePath(
                        owningType,
                        builderElement.getDeclaringType(),
                        propertyElement.getType(),
                        configurationPrefix + propertyName
                    );

                    builderElementDefinitions.add(new ConfigurationBuilderPropertyDefinition(
                        propertyName,
                        methodElement,
                        parameterElement == null ? PrimitiveElement.BOOLEAN : parameterElement.getType(),
                        parameterElement,
                        path));
                } else if (paramCount == 2) {
                    // check the params are a long and a TimeUnit
                    ParameterElement first = params[0];
                    ParameterElement second = params[1];
                    ClassElement firstParamType = first.getType();
                    ClassElement secondParamType = second.getType();

                    if (firstParamType.getSimpleName().equals("long") && secondParamType.isAssignable(TimeUnit.class)) {
                        String path = ConfigurationMetadataBuilder.calculatePath(
                            owningType,
                            methodElement.getDeclaringType(),
                            visitorContext.getClassElement(Duration.class.getName()).get(),
                            configurationPrefix + propertyName
                        );

                        builderElementDefinitions.add(new ConfigurationBuilderPropertyDefinition(
                            propertyName,
                            methodElement,
                            visitorContext.getClassElement(Duration.class.getName()).get(),
                            null,
                            path));

                    }
                }
            });
        return builderElementDefinitions;
    }

    /**
     * @return The builder element
     */
    MemberElement builderElement();

    /**
     * @return The builder type
     */
    ClassElement builderType();

    /**
     * @return The builder elements
     */
    List<ConfigurationBuilderPropertyDefinition> elements();

}
