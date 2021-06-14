/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import javax.inject.Provider;

/**
 * Implementation for javax provider bean lookups.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class JavaxProviderBeanDefinition extends AbstractProviderDefinition<Provider<Object>> {

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return isTypePresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Provider<Object>> getBeanType() {
        return (Class) Provider.class;
    }

    @Override
    public boolean isPresent() {
        return isTypePresent();
    }

    @Override
    protected Provider<Object> buildProvider(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            Argument<Object> argument,
            Qualifier<Object> qualifier,
            boolean singleton) {
        if (singleton) {
            return new Provider<Object>() {
                Object bean;

                @Override
                public Object get() {
                    if (bean == null) {
                        bean = ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
                    }
                    return bean;
                }
            };
        } else {
            return () -> ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
        }
    }

    private static boolean isTypePresent() {
        try {
            return Provider.class.isInterface();
        } catch (Throwable e) {
            // class not present
            return false;
        }
    }
}
