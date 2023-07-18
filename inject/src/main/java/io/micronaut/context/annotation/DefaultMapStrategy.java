/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.context.annotation;

import io.micronaut.core.annotation.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Default strategy.
 *
 * @param conflictStrategy The conflict strategy
 * @param customMappers custom property mappers
 */
record DefaultMapStrategy(@NonNull Mapper.MapStrategy.ConflictStrategy conflictStrategy,
                          @NonNull Map<String, BiFunction<Mapper.MapStrategy, Object, Object>> customMappers) implements Mapper.MapStrategy {
    DefaultMapStrategy() {
        this(Mapper.MapStrategy.ConflictStrategy.CONVERT, Collections.emptyMap());
    }

    DefaultMapStrategy {
        if (customMappers == null) {
            customMappers = Collections.emptyMap();
        } else {
            customMappers = Collections.unmodifiableMap(customMappers);
        }
        if (conflictStrategy == null) {
            conflictStrategy = ConflictStrategy.CONVERT;
        }
    }
}
