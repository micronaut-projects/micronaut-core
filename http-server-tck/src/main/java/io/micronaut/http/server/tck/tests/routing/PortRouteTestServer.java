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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.server.exceptions.ServerStartupException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;

import java.net.BindException;
import java.util.Map;

/** Starts a server with a second port chosen before startup. */
final class PortRouteTestServer {
    private PortRouteTestServer() {
    }

    static ServerUnderTest start(String specName, String portProperty, int firstPort) {
        int port = firstPort;
        int collisions = 0;
        while (true) {
            try {
                return ServerUnderTestProviderUtils.getServerUnderTestProvider()
                    .getServer(specName, Map.of(portProperty, port));
            } catch (ServerStartupException e) {
                if (!(e.getCause() instanceof BindException) || ++collisions == 5) {
                    throw e;
                }
                // A port found available may be claimed before both server listeners bind.
                port = SocketUtils.findAvailableTcpPort();
            }
        }
    }
}
