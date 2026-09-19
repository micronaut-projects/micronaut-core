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
package io.micronaut.context.python.runtime.model;

import org.jspecify.annotations.Nullable;

/**
 * The decisions the build-time writer precalculates for a definition.
 *
 * @param scope                     The scope annotation name
 * @param isAbstract                Whether the bean is abstract
 * @param isIterable                Whether the bean is iterable
 * @param isSingleton               Whether the bean is a singleton
 * @param isPrimary                 Whether the bean is primary
 * @param isConfigurationProperties Whether the bean reads configuration
 * @param isContainerType           Whether the bean type is a container type
 * @since 5.3.0
 */
public record PrecalculatedInfoModel(@Nullable String scope, boolean isAbstract, boolean isIterable, boolean isSingleton,
                                     boolean isPrimary, boolean isConfigurationProperties, boolean isContainerType) {
}
