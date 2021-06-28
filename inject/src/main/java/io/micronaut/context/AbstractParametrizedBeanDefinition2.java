/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ParametrizedBeanFactory;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * A {@link BeanDefinition} that is a {@link ParametrizedBeanFactory}.
 *
 * @param <T> The Bean definition type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public abstract class AbstractParametrizedBeanDefinition2<T> extends AbstractBeanDefinition2<T> implements ParametrizedBeanFactory<T> {

    private final Argument[] requiredArguments;

    @SuppressWarnings("ParameterNumber")
    protected AbstractParametrizedBeanDefinition2(Class<T> beanType, MethodOrFieldReference constructor, AnnotationMetadata annotationMetadata, MethodReference[] methodInjection, FieldReference[] fieldInjection, ExecutableMethod<T, ?>[] executableMethods, Map<String, Argument<?>[]> typeArgumentsMap, Optional<String> scope, boolean isAbstract, boolean isProvided, boolean isIterable, boolean isSingleton, boolean isPrimary) {
        super(beanType, constructor, annotationMetadata, methodInjection, fieldInjection, executableMethods, typeArgumentsMap, scope, isAbstract, isProvided, isIterable, isSingleton, isPrimary);
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
        Optional<Class> eachBeanType = definition.classValue(EachBean.class);
        for (Argument<?> requiredArgument : requiredArguments) {
            if (requiredArgument.getType() == BeanResolutionContext.class) {
                requiredArgumentValues.put(requiredArgument.getName(), resolutionContext);
            }

            BeanResolutionContext.Path path = resolutionContext.getPath();
            try {
                path.pushConstructorResolve(this, requiredArgument);
                String argumentName = requiredArgument.getName();
                if (!requiredArgumentValues.containsKey(argumentName) && !requiredArgument.isNullable()) {
                    if (eachBeanType.filter(type -> type == requiredArgument.getType()).isPresent()) {
                        throw new DisabledBeanException("@EachBean parameter disabled for argument: " + requiredArgument.getName());
                    }
                    throw new BeanInstantiationException(resolutionContext, "Missing bean argument value: " + argumentName);
                }
                Object value = requiredArgumentValues.get(argumentName);
                boolean requiresConversion = value != null && !requiredArgument.getType().isInstance(value);
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

    /**
     * @param resolutionContext      The resolution context
     * @param context                The bean context
     * @param definition             The bean definition
     * @param requiredArgumentValues The required arguments
     * @return The built instance
     */
    protected abstract T doBuild(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition, Map<String, Object> requiredArgumentValues);

    private Argument[] resolveRequiredArguments() {
        return Arrays.stream(getConstructor().getArguments())
            .filter(arg -> {
                Optional<Class<? extends Annotation>> qualifierType = arg.getAnnotationMetadata().getAnnotationTypeByStereotype(AnnotationUtil.QUALIFIER);
                return qualifierType.isPresent() && qualifierType.get() == Parameter.class;
            })
            .toArray(Argument[]::new);
    }
}
