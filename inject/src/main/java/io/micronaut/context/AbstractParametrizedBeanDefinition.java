/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ParametrizedBeanFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link BeanDefinition} that is a {@link ParametrizedBeanFactory}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractParametrizedBeanDefinition<T> extends AbstractBeanDefinition<T> implements ParametrizedBeanFactory<T> {

    private final Argument[] requiredArguments;

    public AbstractParametrizedBeanDefinition(Class<T> producedType, Class<?> declaringType, String methodName, AnnotationMetadata methodMetadata, boolean requiresReflection, Argument... arguments) {
        super(producedType, declaringType, methodName, methodMetadata, requiresReflection, arguments);
        this.requiredArguments = resolveRequiredArguments();
    }

    protected AbstractParametrizedBeanDefinition(Class<T> type,
                                                 AnnotationMetadata annotationMetadata,
                                                 boolean requiresReflection,
                                                 Argument... arguments) {
        super(type, annotationMetadata,requiresReflection, arguments);
        this.requiredArguments = resolveRequiredArguments();
    }

    @Override
    public Argument<?>[] getRequiredArguments() {
        return requiredArguments;
    }

    @Override
    public final T build(BeanResolutionContext resolutionContext,
                         BeanContext context,
                         BeanDefinition<T> definition,
                         Map<String, Object> requiredArgumentValues) throws BeanInstantiationException {

        requiredArgumentValues = requiredArgumentValues != null ? new LinkedHashMap<>(requiredArgumentValues) : Collections.emptyMap();
        Argument<?>[] requiredArguments = getRequiredArguments();
        for (Argument<?> requiredArgument : requiredArguments) {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            try {
                path.pushConstructorResolve(this, requiredArgument);
                String argumentName = requiredArgument.getName();
                if (!requiredArgumentValues.containsKey(argumentName)) {
                    throw new BeanInstantiationException(resolutionContext, "Missing argument value: " + argumentName);
                }
                Object value = requiredArgumentValues.get(argumentName);
                boolean requiresConversion = !requiredArgument.getType().isInstance(value);
                if (requiresConversion) {
                    Optional<?> converted = ConversionService.SHARED.convert(value, requiredArgument.getType(), ConversionContext.of(requiredArgument));
                    Object finalValue = value;
                    value = converted.orElseThrow(() -> new BeanInstantiationException(resolutionContext, "Invalid value [" + finalValue + "] for argument: " + argumentName));
                    requiredArgumentValues.put(argumentName, value);
                }
            } finally {
                path.pop();
            }
        }
        return doBuild(resolutionContext, context, definition, requiredArgumentValues);
    }

    protected abstract T doBuild(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition, Map<String, Object> requiredArgumentValues);

    private Argument[] resolveRequiredArguments() {
        return Arrays.stream(getConstructor().getArguments())
            .filter(arg -> {
                Annotation qualifier = arg.getQualifier();
                return qualifier != null && qualifier.annotationType() == Parameter.class;
            })
            .toArray(Argument[]::new);
    }
}
