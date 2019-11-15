/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.runtime.context.env;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.ReturnType;

import javax.inject.Singleton;

/**
 * Internal introduction advice used to allow {@link io.micronaut.context.annotation.ConfigurationProperties} on interfaces. Considered internal and not for direct use.
 *
 * @author graemerocher
 * @since 1.3.0
 * @see ConfigurationAdvice
 * @see io.micronaut.context.annotation.ConfigurationProperties
 */
@Singleton
@Internal
public class ConfigurationIntroductionAdvice implements MethodInterceptor<Object, Object> {
    private static final String MEMBER_BEAN = "bean";
    private static final String MEMBER_NAME = "name";
    private final Environment environment;
    private final BeanLocator beanLocator;

    /**
     * Default constructor.
     * @param environment The environment
     * @param beanLocator  The bean locator
     */
    ConfigurationIntroductionAdvice(Environment environment, BeanLocator beanLocator) {
        this.environment = environment;
        this.beanLocator = beanLocator;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final ReturnType<Object> rt = context.getReturnType();
        final Class<Object> returnType = rt.getType();
        if (context.isTrue(ConfigurationAdvice.class, MEMBER_BEAN)) {
            if (context.isNullable()) {
                final Object v = beanLocator.findBean(returnType).orElse(null);
                if (v != null) {
                    return environment.convertRequired(v, returnType);
                } else {
                    return v;
                }
            } else {
                return environment.convertRequired(
                        beanLocator.getBean(returnType),
                        returnType
                );
            }
        } else {
            final String property = context.stringValue(Property.class, MEMBER_NAME).orElse(null);
            if (property == null) {
                throw new IllegalStateException("No property name available to resolve");
            }
            final String defaultValue = context.stringValue(Bindable.class, "defaultValue").orElse(null);
            if (defaultValue != null) {
                return environment.getProperty(
                        property,
                        returnType,
                        defaultValue
                );
            } else {
                if (context.isNullable()) {
                    return environment.getProperty(
                            property,
                            returnType
                    ).orElse(null);
                } else {

                    return environment.getRequiredProperty(
                            property,
                            returnType
                    );
                }
            }
        }
    }
}
