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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;

/**
 * Different filter order heuristics, derived from annotations or {@link Ordered#getOrder() a
 * bean method}.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public sealed interface FilterOrder {
    /**
     * Compute the order value for the given bean.
     *
     * @param bean The bean to compute the order value for, potentially implementing
     * {@link Ordered}
     * @return The order value
     */
    int getOrder(Object bean);

    /**
     * Fixed order value.
     *
     * @param value The order value
     */
    record Fixed(int value) implements FilterOrder {
        @Override
        public int getOrder(Object bean) {
            return value;
        }
    }

    /**
     * Dynamic order value (from {@link Ordered#getOrder()}).
     *
     * @param fallbackValue The order value to use if the bean does not implement {@link Ordered}
     */
    record Dynamic(int fallbackValue) implements FilterOrder {
        @Override
        public int getOrder(Object bean) {
            if (bean instanceof Ordered o) {
                return o.getOrder();
            } else {
                return fallbackValue;
            }
        }
    }
}
