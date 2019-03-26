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
package io.micronaut.validation.validator.extractors;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.validation.valueextraction.UnwrapByDefault;
import javax.validation.valueextraction.ValueExtractor;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The default {@link ValueExtractorRegistry} that looks up from the {@link io.micronaut.context.BeanContext}.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Internal
class DefaultValueExtractorRegistry implements ValueExtractorRegistry {

    private final Set<Class> unwrapByDefaultTypes;
    private final BeanContext beanContext;

    /**
     * Default constructor.
     * @param beanContext The bean locator.
     */
    DefaultValueExtractorRegistry(@Nonnull BeanContext beanContext) {
        ArgumentUtils.requireNonNull("beanContext", beanContext);
        this.beanContext = beanContext;
        this.unwrapByDefaultTypes = beanContext.getBeanDefinitions(ValueExtractor.class)
                                           .stream()
                                           .filter(bd ->
                                                   (UnwrapByDefaultValueExtractor.class.isAssignableFrom(bd.getBeanType()) || bd.hasStereotype(UnwrapByDefault.class)) &&
                                                   !bd.getTypeArguments(ValueExtractor.class).isEmpty())
                                           .map(bd -> bd.getTypeParameters(ValueExtractor.class)[0])
                                           .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Optional<ValueExtractor<T>> findValueExtractor(@Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("targetType", targetType);
        return (Optional) beanContext.findBean(ValueExtractor.class, Qualifiers.byTypeArguments(targetType));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Optional<ValueExtractor<T>> findUnwrapValueExtractor(@Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("targetType", targetType);
        if (unwrapByDefaultTypes.contains(targetType)) {
            return (Optional) beanContext.findBean(ValueExtractor.class, Qualifiers.byTypeArguments(targetType));
        }
        return Optional.empty();
    }
}
