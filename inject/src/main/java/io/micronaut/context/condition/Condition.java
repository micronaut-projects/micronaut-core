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
package io.micronaut.context.condition;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Introspected;

import java.util.function.Predicate;

/**
 * A condition allows conditional loading of a {@link io.micronaut.inject.BeanConfiguration}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionalInterface
@Introspected
public interface Condition extends Predicate<ConditionContext> {

    /**
     * Check whether a specific condition is met.
     *
     * @param context The condition context
     * @return True if has been met
     */
    boolean matches(ConditionContext context);

    @Override
    default boolean test(ConditionContext condition) {
        return matches(condition);
    }
}
