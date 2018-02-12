/*
 * Copyright 2017 original authors
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
package org.particleframework.context;

import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ParametrizedBeanFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A {@link BeanDefinition} that is a {@link ParametrizedBeanFactory}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractParametrizedBeanDefinition<T> extends AbstractBeanDefinition<T> implements ParametrizedBeanFactory<T> {

    private final Argument[] requiredArguments;

    protected AbstractParametrizedBeanDefinition(Method method, Argument[] arguments) {
        super(method, arguments);
        this.requiredArguments = resolveRequiredArguments();

    }

    protected AbstractParametrizedBeanDefinition(Class<T> type, Constructor<T> constructor, Argument[] arguments) {
        super(type, constructor, arguments);
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
                if(!requiredArgumentValues.containsKey(argumentName)) {
                    throw new BeanInstantiationException(resolutionContext, "Missing argument value: " + argumentName);
                }
                Object value = requiredArgumentValues.get(argumentName);
                boolean requiresConversion = !requiredArgument.getType().isInstance(value);
                if(requiresConversion) {
                    Optional<?> converted = ConversionService.SHARED.convert(value, requiredArgument.getType(), ConversionContext.of(requiredArgument));
                    Object finalValue = value;
                    value = converted.orElseThrow(()-> new BeanInstantiationException(resolutionContext, "Invalid value [" + finalValue + "] for argument: " + argumentName) );
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
                    return qualifier != null && qualifier.annotationType() == org.particleframework.context.annotation.Argument.class;
                })
                .toArray(Argument[]::new);
    }
}
