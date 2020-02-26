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
package io.micronaut.cli.profile.commands.io

import groovy.transform.CompileStatic

/**
 * Methods to aid interacting with the server from the CLI
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait ServerInteraction {

    /**
     * Waits for the server to startup
     *
     * @param host The host
     * @param port The port
     */
    void waitForStartup(String host = "localhost", int port = 8080) {
        while (!isServerAvailable(host, port)) {
            sleep 100
        }
        try {
            new URL("http://${host ?: 'localhost'}:${port ?: 8080}/is-tomcat-running").text
        } catch (ignored) {
            // ignore
        }
    }

    /**
     * Returns true if the server is available
     *
     * @param host The host
     * @param port The port
     */
    boolean isServerAvailable(String host = "localhost", int port = 8080) {
        try {
            new Socket(host, port)
            return true
        } catch (e) {
            return false
        }
    }
}