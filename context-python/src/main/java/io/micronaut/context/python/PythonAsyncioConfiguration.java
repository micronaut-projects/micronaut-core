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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for Python asyncio integration.
 *
 * @param enabled Whether asyncio integration is enabled.
 */
@ConfigurationProperties(PythonAsyncioConfiguration.PREFIX)
@Experimental
public record PythonAsyncioConfiguration(
    @Bindable(defaultValue = "true") boolean enabled
) {
    /**
     * Configuration prefix for Python asyncio.
     */
    public static final String PREFIX = "micronaut.python.asyncio";

    /**
     * Property used to enable or disable Python asyncio support.
     */
    public static final String ENABLED = PREFIX + ".enabled";
}
