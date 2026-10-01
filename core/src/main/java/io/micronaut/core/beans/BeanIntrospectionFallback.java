/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.order.Ordered;

import java.util.Optional;

/**
 * Supplies a {@link BeanIntrospection} for a type that has no generated one.
 *
 * <p>The default {@link BeanIntrospector} serves the introspections the annotation processors generated. When
 * it has none for a type it asks the fallbacks registered as services, in {@link Ordered order}, and returns the
 * first introspection one of them supplies. A fallback decides for itself which types it serves: the
 * {@code micronaut-reflection} module registers one that describes a class reflectively, but only the classes an
 * application allowed by configuration.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
@Indexed(BeanIntrospectionFallback.class)
public interface BeanIntrospectionFallback extends Ordered {

    /**
     * Finds an introspection for a type the introspector has no generated introspection for.
     *
     * @param beanType The bean type
     * @param <T>      The bean type
     * @return The introspection, or empty when this fallback does not serve the type
     */
    <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType);
}
