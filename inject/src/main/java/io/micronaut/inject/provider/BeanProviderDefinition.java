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

import io.micronaut.context.*;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.AnyQualifier;

import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Implementation for {@link BeanProvider} bean lookups.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class BeanProviderDefinition extends AbstractProviderDefinition<BeanProvider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<BeanProvider<Object>> getBeanType() {
        return (Class) BeanProvider.class;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    protected BeanProvider<Object> buildProvider(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull BeanContext context,
            @NonNull Argument<Object> argument,
            @Nullable Qualifier<Object> qualifier,
            boolean singleton) {
        return new BeanProvider<Object>() {
            private final Qualifier<Object> finalQualifier =
                    qualifier instanceof AnyQualifier ? null : qualifier;

            @Override
            public Object get() {
                return ((DefaultBeanContext) context).getBean(resolutionContext, argument, finalQualifier);
            }

            @Override
            public Object get(Qualifier<Object> qualifier) {
                return ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
            }

            @Override
            public boolean isUnique() {
                try {
                    return context.getBeanDefinitions(argument, finalQualifier).size() == 1;
                } catch (NoSuchBeanException e) {
                    return false;
                }
            }

            @Override
            public boolean isPresent() {
                return context.containsBean(argument, finalQualifier);
            }

            @NonNull
            @Override
            public Iterator<Object> iterator() {
                return ((DefaultBeanContext) context).getBeansOfType(resolutionContext, argument, finalQualifier).iterator();
            }

            @Override
            public Stream<Object> stream() {
                return ((DefaultBeanContext) context).streamOfType(resolutionContext, argument, finalQualifier);
            }
        };
    }

    @Override
    protected boolean isAllowEmptyProviders(BeanContext context) {
        return true;
    }
}
