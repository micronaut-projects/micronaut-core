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
package io.micronaut.dev.livereload;

import io.micronaut.core.annotation.Experimental;

import java.io.IOException;

/**
 * Starts a {@link LiveReloadServer}. Registered as a service; the launcher starts the first one it
 * finds on its classpath, so adding {@code micronaut-dev-livereload} to the development runtime
 * classpath is what turns LiveReload on.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface LiveReloadServerFactory {

    /**
     * Starts a server on the loopback address.
     *
     * @param port The port, {@link LiveReloadServer#DEFAULT_PORT} for the extensions; 0 for any free port
     * @return The started server
     * @throws IOException if the port cannot be bound
     */
    LiveReloadServer start(int port) throws IOException;
}
