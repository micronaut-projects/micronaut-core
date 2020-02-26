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
package io.micronaut.cache;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.naming.Named;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Provides information about the state of the cache.
 *
 * @author graemerocher
 * @since 1.1
 */
public interface CacheInfo extends Named {

    /**
     * A publisher that emits a single result containing the cache data.
     * @return Cache data
     */
    @Nonnull
    @SingleResult
    Map<String, Object> get();
}
