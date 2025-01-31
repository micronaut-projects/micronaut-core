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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Iterator;
import java.util.Optional;
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
        return new BeanProvider<>() {

            private final DefaultBeanContext defaultBeanContext = (DefaultBeanContext) context;
            private final Qualifier<Object> finalQualifier =
                    qualifier instanceof AnyQualifier ? null : qualifier;

            private Qualifier<Object> qualify(Qualifier<Object> qualifier) {
                if (finalQualifier == null) {
                    return qualifier;
                } else if (qualifier == null) {
                    return finalQualifier;
                }

                //noinspection unchecked
                return Qualifiers.byQualifiers(finalQualifier, qualifier);
            }

            @Override
            public Object get() {
                return defaultBeanContext.getBean(resolutionContext.copy(), argument, finalQualifier);
            }

            @Override
            public Optional<Object> find(Qualifier<Object> qualifier) {
                return defaultBeanContext.findBean(resolutionContext.copy(), argument, qualify(qualifier));
            }

            @Override
            public BeanDefinition<Object> getDefinition() {
                return context.getBeanDefinition(argument, finalQualifier);
            }

            @Override
            public Object get(Qualifier<Object> qualifier) {
                return defaultBeanContext.getBean(resolutionContext.copy(), argument, qualify(qualifier));
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
                return defaultBeanContext.getBeansOfType(resolutionContext.copy(), argument, finalQualifier).iterator();
            }

            @Override
            public Stream<Object> stream() {
                return defaultBeanContext.streamOfType(resolutionContext.copy(), argument, finalQualifier);
            }

            @Override
            public String toString() {
                return "Provider(" + argument + ")";
            }
        };
    }

    @Override
    protected boolean isAllowEmptyProviders(BeanContext context) {
        return true;
    }

}
