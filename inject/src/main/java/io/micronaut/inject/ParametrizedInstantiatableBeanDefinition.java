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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>An type of {@link BeanDefinition} that can build a new instance, construction requires additional (possibly user supplied) parameters in order construct a bean</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 4.0
 */
@Internal
public interface ParametrizedInstantiatableBeanDefinition<T> extends InstantiatableBeanDefinition<T> {

    /**
     * @return The arguments required to construct this bean
     */
    default Argument<Object>[] getRequiredArguments() {
        return resolveRequiredArguments(this);
    }

    /**
     * Variation of the {@link #instantiate(BeanContext)} method that allows passing the values necessary for
     * successful bean construction.
     *
     * @param resolutionContext      The {@link BeanResolutionContext}
     * @param context                The {@link BeanContext}
     * @param requiredArgumentValues The required arguments values. The keys should match the names of the arguments
     *                               returned by {@link #getRequiredArguments()}
     * @return The instantiated bean
     * @throws BeanInstantiationException If the bean cannot be instantiated for the arguments supplied
     */
    default T instantiate(BeanResolutionContext resolutionContext,
                          BeanContext context,
                          Map<String, Object> requiredArgumentValues) throws BeanInstantiationException {
        return doInstantiate(
            resolutionContext,
            context,
            resolveParameterizedArgumentValues(resolutionContext, this, requiredArgumentValues)
        );
    }

    /**
     * Method to be implemented by the generated code if the bean definition is implementing {@link io.micronaut.inject.ParametrizedInstantiatableBeanDefinition}.
     *
     * @param resolutionContext      The resolution context
     * @param context                The bean context
     * @param requiredArgumentValues The required arguments
     * @return The built instance
     */
    default T doInstantiate(BeanResolutionContext resolutionContext, BeanContext context, Map<String, Object> requiredArgumentValues) {
        throw new IllegalStateException("Method must be implemented for 'ParametrizedInstantiatableBeanDefinition' instance!");
    }

    @Override
    default T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        throw new BeanInstantiationException(this, "Cannot instantiate parametrized bean with no arguments");
    }

    /**
     * Resolve required arguments.
     *
     * @param parametrizedInstantiatableBeanDefinition The parameterized bean definition
     * @return the required arguments
     * @since 5.0
     */
    static Argument<Object>[] resolveRequiredArguments(ParametrizedInstantiatableBeanDefinition<?> parametrizedInstantiatableBeanDefinition) {
        Argument<?>[] arguments = parametrizedInstantiatableBeanDefinition.getConstructor().getArguments();
        List<Argument<Object>> list = new ArrayList<>(arguments.length);
        for (Argument<?> argument : arguments) {
            Optional<String> qualifierType = AnnotationUtil.findQualifierAnnotation(argument.getAnnotationMetadata());
            if (qualifierType.isPresent() && qualifierType.get().equals(Parameter.class.getName())) {
                list.add((Argument<Object>) argument);
            }
        }
        return list.toArray(Argument.ZERO_ARGUMENTS);
    }

    /**
     * Resolve or convert arguments for the bean
     *
     * @param resolutionContext                        The resolution context
     * @param parametrizedInstantiatableBeanDefinition The parameterized bean definition
     * @param requiredArgumentValues                   The required arguments
     * @return the required arguments
     * @since 5.0
     */
    static Map<String, Object> resolveParameterizedArgumentValues(BeanResolutionContext resolutionContext,
                                                                  ParametrizedInstantiatableBeanDefinition<?> parametrizedInstantiatableBeanDefinition,
                                                                  Map<String, Object> requiredArgumentValues) {
        for (Argument<?> requiredArgument : parametrizedInstantiatableBeanDefinition.getRequiredArguments()) {
            String argumentName = requiredArgument.getName();
            Object value = requiredArgumentValues.get(argumentName);
            if (value == null && !requiredArgument.isNullable()) {
                try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(parametrizedInstantiatableBeanDefinition, requiredArgument)) {
                    Class<?> eachBeanType = parametrizedInstantiatableBeanDefinition.classValue(EachBean.class).orElse(null);
                    if (eachBeanType == requiredArgument.getType()) {
                        throw new DisabledBeanException("@EachBean parameter disabled for argument: " + requiredArgument.getName());
                    }
                    throw new BeanInstantiationException(resolutionContext, "Missing bean argument value: " + argumentName);
                }
            }
            boolean requiresConversion = value != null && !requiredArgument.getType().isInstance(value);
            if (requiresConversion) {
                Optional<?> converted = resolutionContext.getConversionService().convert(value, requiredArgument.getType(), ConversionContext.of(requiredArgument));
                Object finalValue = value;
                value = converted.orElseThrow(() -> {
                    try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(parametrizedInstantiatableBeanDefinition, requiredArgument)) {
                        return new BeanInstantiationException(resolutionContext, "Invalid value [" + finalValue + "] for argument: " + argumentName);
                    }
                });
                requiredArgumentValues = new LinkedHashMap<>(requiredArgumentValues); // Make mutable
                requiredArgumentValues.put(argumentName, value);
            }
        }
        return requiredArgumentValues;
    }
}
