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
package io.micronaut.runtime.context.env;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.value.PropertyNotFoundException;
import io.micronaut.inject.BeanDefinition;

import java.util.Collections;
import java.util.Optional;

/**
 * Internal introduction advice used to allow {@link io.micronaut.context.annotation.ConfigurationProperties} on interfaces. Considered internal and not for direct use.
 *
 * @author graemerocher
 * @see ConfigurationAdvice
 * @see io.micronaut.context.annotation.ConfigurationProperties
 * @since 1.3.0
 */
@Prototype
@Internal
@BootstrapContextCompatible
@InterceptorBean(ConfigurationAdvice.class)
public class ConfigurationIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private static final String MEMBER_BEAN = "bean";
    private static final String MEMBER_NAME = "name";
    private final Environment environment;
    private final ConversionService conversionService;
    private final BeanContext beanContext;
    private final ConfigurationPath configurationPath;
    private final BeanDefinition<?> beanDefinition;

    /**
     * Default constructor.
     *
     * @param resolutionContext The resolution context
     * @param environment       The environment
     * @param beanContext       The bean locator
     */
    ConfigurationIntroductionAdvice(
        BeanResolutionContext resolutionContext,
        Environment environment,
        BeanContext beanContext) {
        this.beanDefinition = resolutionContext.getRootDefinition();
        this.environment = environment;
        this.conversionService = environment.getConversionService();
        this.beanContext = beanContext;
        this.configurationPath = resolutionContext.getConfigurationPath().copy();
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final ReturnType<Object> rt = context.getReturnType();
        final Class<Object> returnType = rt.getType();
        final Argument<Object> argument = rt.asArgument();
        if (context.isTrue(ConfigurationAdvice.class, MEMBER_BEAN)) {
            return resolveBean(context, returnType, argument);
        } else {
            return resolveProperty(context, rt, argument);
        }
    }

    private Object resolveProperty(MethodInvocationContext<Object, Object> context, ReturnType<Object> rt, Argument<Object> argument) {
        String property = context.stringValue(Property.class, MEMBER_NAME).orElse(null);
        if (property == null) {
            throw new IllegalStateException("No property name available to resolve");
        }
        if (configurationPath.hasDynamicSegments()) {
            property = configurationPath.resolveValue(property);
        }
        final String defaultValue = context.stringValue(Bindable.class, "defaultValue").orElse(null);

        final Optional<Object> value = environment.getProperty(
            property,
            argument
        );

        if (defaultValue != null) {
            Object result = value.orElse(null);
            if (result == null) {
                return conversionService.convertRequired(defaultValue, argument);
            }
            return result;
        } else if (rt.isOptional()) {
            return value.orElse(Optional.empty());
        } else if (context.isNullable()) {
            return value.orElse(null);
        } else {
            String finalProperty = property;
            return value.orElseThrow(() -> new PropertyNotFoundException(finalProperty, argument.getType()));
        }
    }

    private Object resolveBean(MethodInvocationContext<Object, Object> context, Class<Object> returnType, Argument<Object> argument) {
        final Qualifier<Object> qualifier = configurationPath.beanQualifier();
        if (Iterable.class.isAssignableFrom(returnType)) {
            @SuppressWarnings("unchecked")
            Argument<Object> firstArg = (Argument<Object>) argument.getFirstTypeVariable().orElse(null);
            if (firstArg != null) {
                return conversionService.convertRequired(beanContext.getBeansOfType(firstArg, qualifier), argument);
            } else {
                return conversionService.convertRequired(Collections.emptyMap(), argument);
            }
        } else if (context.isNullable()) {
            final Object v = beanContext.findBean(argument, qualifier).orElse(null);
            if (v != null) {
                return conversionService.convertRequired(v, returnType);
            } else {
                return v;
            }
        } else {
            try (BeanResolutionContext rc = new DefaultBeanResolutionContext(beanContext, beanDefinition)) {
                rc.setConfigurationPath(configurationPath);
                return conversionService.convertRequired(
                    rc.getBean(argument, qualifier),
                    returnType
                );
            }
        }
    }
}
