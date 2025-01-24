/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;

/**
 * Some helper method for log / exception messages.
 *
 * @since 4.8.0
 */
public final class MessageUtils {

    private MessageUtils() {
    }

    /**
     * Return BeanDefinition type string or target type class name for ProxyBeanDefinition.
     *
     * @param beanDefinition bean definition
     *
     * @return normalized bean definition name
     */
    public static String getNormalizedTypeString(@NonNull BeanDefinition<?> beanDefinition) {

        if (beanDefinition instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
            return proxyBeanDefinition.getTargetType().getSimpleName();
        } else {
            return beanDefinition.asArgument().getTypeString(true);
        }
    }
}
