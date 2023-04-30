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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.List;

/**
 * Data about a disabled bean. Used to improve error reporting.
 *
 * @param type The bean type
 * @param qualifier The qualifier
 * @param reasons The reasons the bean is disabled
 * @param <T> The bean type
 */
public record DisabledBean<T>(
    @NonNull Argument<T> type,
    @Nullable Qualifier<T> qualifier,
    @NonNull List<String> reasons)
    implements BeanDefinition<T>, BeanDefinitionReference<T> {

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public boolean isConfigurationProperties() {
        return BeanDefinition.super.isConfigurationProperties();
    }

    @Override
    public boolean isSingleton() {
        return BeanDefinition.super.isSingleton();
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        return qualifier;
    }

    @Override
    public Class<T> getBeanType() {
        return type.getType();
    }

    @Override
    public Argument<T> asArgument() {
        return type;
    }

    @Override
    public Argument<T> getGenericBeanType() {
        return type;
    }

    @Override
    public String getBeanDefinitionName() {
        return type.getTypeName();
    }

    @Override
    public BeanDefinition<T> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public int hashCode() {
        return type.typeHashCode();
    }
}
