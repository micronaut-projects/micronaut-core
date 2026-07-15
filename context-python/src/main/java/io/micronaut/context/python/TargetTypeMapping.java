/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.NonNull;

/**
 * Strategy interface that defines a host target type mapping for GraalVM HostAccess.
 * Implementations are discovered as beans and registered with HostAccess so that
 * Value.as(TargetType) can convert guest values to the desired host type.
 *
 * @param <T> The target type
 * @since 5.2.0
 */
@Experimental
public interface TargetTypeMapping<T> {
    /**
     * @return The exact target class this mapping supports
     */
    @NonNull Class<T> targetType();

    /**
     * @return Additional erased Java target classes this mapping can satisfy.
     */
    @NonNull
    default Class<?>[] assignableTargetTypes() {
        return new Class<?>[0];
    }

    /**
     * Convert the given polyglot value to the target type.
     * Invoked by HostAccess when resolving Value.as(targetType).
     *
     * @param value The guest value to convert
     * @return The converted host value
     */
    @NonNull T convert(@NonNull Value value);
}
