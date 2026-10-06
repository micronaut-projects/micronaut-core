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
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;

import java.net.BindException;
import java.util.Map;

/**
 * Starts a server under test that also listens on an extra, free port given as a property.
 *
 * <p>The port is only free when it is picked: another process may bind it before the server
 * does, so a server that fails with a {@link BindException} is started again on a new port.</p>
 *
 * @param server The server under test
 * @param port The extra port the server listens on
 */
record ExtraPortServer(ServerUnderTest server, int port) {
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Starts the server of a spec with the extra port as the given property.
     *
     * @param specName The spec name
     * @param portProperty The property the extra port is given as
     * @return The started server and its extra port
     */
    static ExtraPortServer start(String specName, String portProperty) {
        for (int attempt = 1; ; attempt++) {
            int port = SocketUtils.findAvailableTcpPort();
            try {
                return new ExtraPortServer(ServerUnderTestProviderUtils.getServerUnderTestProvider()
                    .getServer(specName, Map.of(portProperty, port)), port);
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS || !isBindFailure(e)) {
                    throw e;
                }
            }
        }
    }

    private static boolean isBindFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BindException) {
                return true;
            }
        }
        return false;
    }
}
