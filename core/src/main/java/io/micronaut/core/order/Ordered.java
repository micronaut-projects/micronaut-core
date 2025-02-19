/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.order;

/**
 * Interface for objects that are ordered.
 *
 * <p>Provides a programmatic alternative to {@link io.micronaut.core.annotation.Order}.</p>
 *
 * <p>Note that this interface only applies to injected collection types since the beans have
 * to be instantiated to resolve the order therefore unlike {@link io.micronaut.core.annotation.Order}
 * it cannot be used for the purposes of prioritizing bean selection.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 * @see io.micronaut.core.annotation.Order
 */
public interface Ordered {
    /**
     * Constant for the highest precedence value.
     *
     * @see java.lang.Integer#MIN_VALUE
     */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * Constant for the lowest precedence value.
     *
     * @see java.lang.Integer#MAX_VALUE
     */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /**
     * @return The order of the object. Defaults to zero (no order).
     */
    default int getOrder() {
        return 0;
    }
}
