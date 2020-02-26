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
package io.micronaut.cache.interceptor;

import io.micronaut.core.annotation.AnnotationMetadata;


/**
 * <p>An interface for generating keys used by {@link io.micronaut.cache.annotation.Cacheable}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface CacheKeyGenerator {

    /**
     * Generate a key for the given annotated element and parameters.
     *
     * @param annotationMetadata The annotated metadata
     * @param params           The parameters
     * @return The generated key. Never null.
     */
    Object generateKey(AnnotationMetadata annotationMetadata, Object... params);
}
