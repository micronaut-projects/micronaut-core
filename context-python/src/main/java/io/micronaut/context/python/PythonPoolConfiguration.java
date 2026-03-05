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
package io.micronaut.context.python;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the PythonPool.
 *
 * @param size       The size of the pool. Defaults to the number of processors * 2.
 * @param syncInit   Whether to synchronously create the pool
 * @param warnWaitMs The amount of time to wait for a pooled context before a warning is printed.
 */
@ConfigurationProperties("micronaut.python.pool")
public record PythonPoolConfiguration(
    @Bindable(defaultValue = "true") boolean enabled,
    @Bindable(defaultValue = "0") int size,
    @Bindable(defaultValue = "false") boolean syncInit,
    @Bindable(defaultValue = "2000") long warnWaitMs
) {
}
