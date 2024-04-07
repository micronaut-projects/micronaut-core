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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;

/**
 * Top level interface for obtaining bean information.
 *
 * @param <T> The type of the bean
 * @since 4.0.0
 */
public interface BeanInfo<T> extends AnnotationMetadataProvider, ArgumentCoercible<T>, Ordered {
    /**
     * @return The bean type
     */
    @NonNull
    Class<T> getBeanType();

    /**
     * @return The generic bean type
     */
    @NonNull
    default Argument<T> getGenericBeanType() {
        return Argument.of(getBeanType());
    }

    @Override
    default Argument<T> asArgument() {
        return getGenericBeanType();
    }

    @Override
    default int getOrder() {
        return OrderUtil.getOrder(getAnnotationMetadata());
    }
}
