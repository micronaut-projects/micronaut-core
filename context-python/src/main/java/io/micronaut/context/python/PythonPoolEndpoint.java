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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

/**
 * Serves the {@link PythonPoolStatistics} snapshot at {@code /pythonpool} when the management module
 * is on the classpath. The endpoint is sensitive by default; set
 * {@code endpoints.pythonpool.sensitive=false} to expose it without authentication.
 *
 * @since 5.2.0
 */
@Endpoint(id = PythonPoolEndpoint.NAME, defaultEnabled = true, defaultSensitive = true)
@Requires(classes = Endpoint.class)
@Experimental
public class PythonPoolEndpoint {

    /**
     * The endpoint id.
     */
    public static final String NAME = "pythonpool";

    private final PythonContextExecutor executor;

    /**
     * @param executor The pool
     */
    public PythonPoolEndpoint(PythonContextExecutor executor) {
        this.executor = executor;
    }

    /**
     * @return The current pool statistics
     */
    @Read
    public PythonPoolStatistics statistics() {
        return executor.statistics();
    }
}
