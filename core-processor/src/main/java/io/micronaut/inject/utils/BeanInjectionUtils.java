/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.utils;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeanInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeanRegistrationInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeanRegistrationsInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeansInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.MapOfBeansInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.OptionalBeanInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.ParameterInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.StreamOfBeansInjectionPoint;
import io.micronaut.context.bean.definition.builder.ConstructorDefinition;
import io.micronaut.context.bean.definition.builder.FieldDefinition;
import io.micronaut.context.bean.definition.builder.MethodDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Utility methods for creating {@link io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint} instances.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
public class BeanInjectionUtils {

    /**
     * Creates a {@link FieldDefinition} describing injection for the supplied field.
     *
     * @param beanType            The declaring bean type
     * @param fieldElement        The field element
     * @param requiresReflection  Whether reflective access is required
     * @param visitorContext      The visitor context
     * @return The field definition
     */
    public static FieldDefinition<ClassElement, FieldElement> createFieldDefinition(ClassElement beanType,
                                                                                    FieldElement fieldElement,
                                                                                    boolean requiresReflection,
                                                                                    VisitorContext visitorContext) {
        return new FieldDefinition<>(
            fieldElement,
            fieldElement,
            BeanInjectionUtils.getInjectionPoint(beanType, fieldElement.getGenericType(), fieldElement, fieldElement.getName(), visitorContext),
            requiresReflection,
            false
        );
    }

    /**
     * Creates a {@link MethodDefinition} describing injection for the supplied method.
     *
     * @param beanType            The declaring bean type
     * @param methodElement       The method element
     * @param annotationMetadata  The method annotation metadata
     * @param requiresReflection  Whether reflective access is required
     * @param visitorContext      The visitor context
     * @return The method definition
     */
    public static MethodDefinition<ClassElement, MethodElement> createMethodDefinition(ClassElement beanType,
                                                                                       MethodElement methodElement,
                                                                                       AnnotationMetadata annotationMetadata,
                                                                                       boolean requiresReflection,
                                                                                       VisitorContext visitorContext) {
        return new MethodDefinition<>(
            methodElement,
            annotationMetadata,
            Arrays.stream(methodElement.getSuspendParameters()).map(p -> BeanInjectionUtils.getInjectionPoint(beanType, p.getGenericType(), p, p.getName(), visitorContext)).toList(),
            requiresReflection
        );
    }

    /**
     * Creates a {@link ConstructorDefinition} for the supplied constructor.
     *
     * @param constructorElement The constructor element
     * @param visitorContext     The visitor context
     * @return The constructor definition
     */
    public static ConstructorDefinition<ClassElement, MethodElement> createConstructorDefinition(MethodElement constructorElement, VisitorContext visitorContext) {
        return createConstructorDefinition(constructorElement, visitorContext, !constructorElement.isAccessible());
    }

    /**
     * Creates a {@link ConstructorDefinition} for the supplied constructor.
     *
     * @param constructorElement  The constructor element
     * @param visitorContext      The visitor context
     * @param requiresReflection  Whether reflective access is required
     * @return The constructor definition
     */
    public static ConstructorDefinition<ClassElement, MethodElement> createConstructorDefinition(MethodElement constructorElement,
                                                                                                 VisitorContext visitorContext,
                                                                                                 boolean requiresReflection) {
        return createConstructorDefinition(
            constructorElement,
            constructorElement,
            visitorContext,
            requiresReflection);
    }

    /**
     * Creates a {@link ConstructorDefinition} using the provided annotation metadata.
     *
     * @param constructorElement  The constructor element
     * @param annotationMetadata  The annotation metadata
     * @param visitorContext      The visitor context
     * @param requiresReflection  Whether reflective access is required
     * @return The constructor definition
     */
    public static ConstructorDefinition<ClassElement, MethodElement> createConstructorDefinition(MethodElement constructorElement,
                                                                                                 AnnotationMetadata annotationMetadata,
                                                                                                 VisitorContext visitorContext,
                                                                                                 boolean requiresReflection) {
        return new ConstructorDefinition<>(
            constructorElement,
            annotationMetadata,
            Arrays.stream(constructorElement.getParameters()).map(p -> BeanInjectionUtils.getInjectionPoint(constructorElement.getOwningType(), p.getGenericType(), p, p.getName(), visitorContext)).toList(),
            requiresReflection
        );
    }

    /**
     * Creates an injection point for {@link Value} or {@link Property}-driven injection.
     *
     * @param beanType            The bean type declaring the injection point
     * @param genericType         The injected type
     * @param annotationMetadata  The associated annotation metadata
     * @param requiresReflection  Whether reflective access is required
     * @param name                The element name
     * @param visitorContext      The visitor context
     * @return The injection point
     */
    public static BeanDefinitionInjectionPoint<ClassElement> createValueInjectionPoint(ClassElement beanType,
                                                                                       ClassElement genericType,
                                                                                       AnnotationMetadata annotationMetadata,
                                                                                       boolean requiresReflection,
                                                                                       String name,
                                                                                       VisitorContext visitorContext) {
        BeanDefinitionInjectionPoint<ClassElement> injectionPoint;
        if (isInnerType(beanType, genericType)) {
            injectionPoint = getInjectionPoint(beanType, genericType, annotationMetadata, name, visitorContext);
        } else if (!isConfigurationProperties(beanType) || requiresReflection) {
            injectionPoint = createPropertyOrValueInjectionPoint(genericType, annotationMetadata, name);
            if (injectionPoint == null) {
                throw new IllegalStateException("Expected a property or a value");
            }

        } else {
            injectionPoint = createPropertyInjection(genericType, annotationMetadata, name);
        }
        return injectionPoint;
    }

    private static boolean isInnerType(ClassElement beanType, ClassElement genericType) {
        String type;
        if (genericType.isContainerType()) {
            type = genericType.getFirstTypeArgument().map(Element::getName).orElse("");
        } else if (genericType.isArray()) {
            type = genericType.fromArray().getName();
        } else {
            type = genericType.getName();
        }
        return beanType.getEnclosedElements(ElementQuery.of(ClassElement.class))
            .stream()
            .filter(BeanInjectionUtils::isConfigurationProperties)
            .map(Element::getName)
            .anyMatch(innerType -> innerType.equals(type));
    }

    /**
     * Resolves the appropriate {@link BeanDefinitionInjectionPoint} for the given element.
     *
     * @param beanType            The bean type declaring the injection point
     * @param genericType         The injected type
     * @param annotationMetadata  The associated annotation metadata
     * @param parameterName       The element name
     * @param visitorContext      The visitor context
     * @return The injection point
     */
    public static BeanDefinitionInjectionPoint<ClassElement> getInjectionPoint(ClassElement beanType,
                                                                               ClassElement genericType,
                                                                               AnnotationMetadata annotationMetadata,
                                                                               String parameterName,
                                                                               VisitorContext visitorContext) {
        if (isAnnotatedWithParameter(annotationMetadata)) {
            return new ParameterInjectionPoint<>(genericType, annotationMetadata, parameterName);
        }
        boolean isArray;
        if (!isInnerType(beanType, genericType)) {
            BeanDefinitionInjectionPoint<ClassElement> result = createPropertyOrValueInjectionPoint(genericType, annotationMetadata, parameterName);
            if (result != null) {
                return result;
            }
        }
        isArray = genericType.isArray();
        if (genericType.isAssignable(Collection.class) || isArray) {
            ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
            if (typeArgument != null && !typeArgument.isPrimitive()) {
                if (typeArgument.isAssignable(BeanRegistration.class)) {
                    return new BeanRegistrationsInjectionPoint<>(genericType, annotationMetadata, typeArgument.getFirstTypeArgument().orElseThrow());
                } else {
                    return new BeansInjectionPoint<>(genericType, annotationMetadata, typeArgument);
                }
            } else {
                return new BeanInjectionPoint<>(genericType, annotationMetadata);
            }
        } else if (isInjectableMap(genericType)) {
            Map<String, ClassElement> mapArguments = genericType.getTypeArguments(Map.class);
            ClassElement objectType = objectType(visitorContext);
            ClassElement injectBeanType = mapArguments.getOrDefault("V", objectType);
            return new MapOfBeansInjectionPoint<>(genericType, annotationMetadata, injectBeanType);
        } else if (genericType.isAssignable(Stream.class)) {
            ClassElement objectType = objectType(visitorContext);
            ClassElement injectBeanType = genericType.getFirstTypeArgument().orElse(objectType);
            return new StreamOfBeansInjectionPoint<>(genericType, annotationMetadata, injectBeanType);
        } else if (genericType.isAssignable(Optional.class)) {
            ClassElement objectType = objectType(visitorContext);
            ClassElement injectBeanType = genericType.getFirstTypeArgument().orElse(objectType);
            return new OptionalBeanInjectionPoint<>(genericType, annotationMetadata, injectBeanType);
        } else if (genericType.isAssignable(BeanRegistration.class)) {
            ClassElement objectType = objectType(visitorContext);
            ClassElement injectBeanType = genericType.getFirstTypeArgument().orElse(objectType);
            return new BeanRegistrationInjectionPoint<>(genericType, annotationMetadata, injectBeanType);
        }
        return new BeanInjectionPoint<>(genericType, annotationMetadata);
    }

    private static ClassElement objectType(VisitorContext visitorContext) {
        return visitorContext.getClassElement(Object.class).orElseThrow();
    }

    @Nullable
    private static BeanDefinitionInjectionPoint<ClassElement> createPropertyOrValueInjectionPoint(ClassElement genericType, AnnotationMetadata annotationMetadata, String parameterName) {
        if (annotationMetadata.hasDeclaredStereotype(Property.class)) {
            return createPropertyInjection(genericType, annotationMetadata, parameterName);
        }
        if (annotationMetadata.hasDeclaredStereotype(Value.class)) {
            return createValueInjection(genericType, annotationMetadata);
        }
        return null;
    }

    private static BeanDefinitionInjectionPoint.PropertyInjectionPoint<ClassElement> createPropertyInjection(ClassElement genericType, AnnotationMetadata annotationMetadata, String parameterName) {
        return new BeanDefinitionInjectionPoint.PropertyInjectionPoint<>(
            genericType,
            annotationMetadata,
            parameterName,
            findProperty(annotationMetadata)
                .orElseThrow(() -> new ProcessingException(annotationMetadata instanceof Element element ? element : null, "Property [" + parameterName + "] injection requires a value"))
        );
    }

    private static Optional<String> findProperty(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.stringValue(Property.class, "name");
    }

    private static BeanDefinitionInjectionPoint.ValueInjectionPoint<ClassElement> createValueInjection(ClassElement genericType, AnnotationMetadata annotationMetadata) {
        return new BeanDefinitionInjectionPoint.ValueInjectionPoint<>(
            genericType,
            annotationMetadata,
            annotationMetadata.stringValue(Value.class)
                .orElseThrow(() -> new ProcessingException(
                    annotationMetadata instanceof Element element ? element : null,
                    "Value injection requires a value")),
            annotationMetadata.getValue(Value.class, EvaluatedExpressionReference.class).isPresent()
        );
    }

    private static boolean isConfigurationProperties(AnnotationMetadata annotationMetadata) {
        return isIterable(annotationMetadata) || annotationMetadata.hasStereotype(ConfigurationReader.class);
    }

    private static boolean isIterable(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredStereotype(EachProperty.class) || annotationMetadata.hasDeclaredStereotype(EachBean.class);
    }

    private static boolean isAnnotatedWithParameter(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredAnnotation(Parameter.class);
    }

    private static boolean isInjectableMap(ClassElement genericType) {
        boolean typeMatches = Stream.of(Map.class, HashMap.class, LinkedHashMap.class, TreeMap.class)
            .anyMatch(t -> genericType.getName().equals(t.getName()));
        if (typeMatches) {

            Map<String, ClassElement> typeArgs = genericType.getTypeArguments();
            if (typeArgs.size() == 2) {
                ClassElement k = typeArgs.get("K");
                return k != null && k.isAssignable(CharSequence.class);
            }
        }
        return false;
    }
}
