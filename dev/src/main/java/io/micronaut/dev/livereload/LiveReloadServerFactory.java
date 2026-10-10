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
import java.util.List;

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

    /**
     * Starts a server on the loopback address whose LiveReload socket also accepts the pages of the origins given:
     * those of an application opened through a name other than {@code localhost}, a {@code *.localhost} name or the
     * loopback address, such as a LAN host or the name of a container. Each is an origin,
     * {@code http://devbox.lan:8080}, or a host name, {@code devbox.lan}, of any port; no pattern is accepted. The
     * clients the socket always accepts are accepted whatever is given. A factory that does not override this
     * accepts no other origin.
     *
     * @param port The port, {@link LiveReloadServer#DEFAULT_PORT} for the extensions; 0 for any free port
     * @param allowedOrigins The origins or host names whose pages may follow the socket, beside the loopback ones
     * @return The started server
     * @throws IOException if the port cannot be bound
     */
    default LiveReloadServer start(int port, List<String> allowedOrigins) throws IOException {
        return start(port);
    }
}
