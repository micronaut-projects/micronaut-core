/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.beans;

import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * Simple class that provides a map interface over a bean.
 *
 * @param <T> type Generic
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanMap<T> extends Map<String, Object> {

    /**
     * @return The bean type
     */
    @NonNull Class<T> getBeanType();

    /**
     * Creates a {@link BeanMap} for the given bean.
     *
     * @param bean The bean
     * @param <B> type Generic
     * @return The bean map
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    static @NonNull <B> BeanMap<B> of(@NonNull B bean) {
        ArgumentUtils.requireNonNull("bean", bean);
        return BeanIntrospector.SHARED.findIntrospection(bean.getClass())
                .map(i -> (BeanMap<B>) new BeanIntrospectionMap<>((BeanIntrospection<B>) i, bean))
                .orElseGet(() -> new ReflectionBeanMap<>(bean));
    }
}
