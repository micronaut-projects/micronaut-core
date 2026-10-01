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

import java.io.Closeable;
import java.nio.file.Path;

/**
 * A running LiveReload server: browsers connected to it reload the page, or swap a stylesheet, on
 * request. The implementation comes from a {@link LiveReloadServerFactory} on the development
 * runtime classpath, {@code micronaut-dev-livereload}; without one there is no server.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface LiveReloadServer extends Closeable {

    /**
     * The port the LiveReload extensions expect.
     */
    int DEFAULT_PORT = 35729;

    /**
     * The path of the client script the server serves.
     */
    String SCRIPT_PATH = "/livereload.js";

    /**
     * The WebSocket path of the event channel: a page connects to {@code /micronaut-dev/events?topic=<topic>} to receive
     * what is {@link #publish published} on that topic. It is apart from the LiveReload socket, whose clients reject a
     * command they do not know.
     */
    String EVENTS_PATH = "/micronaut-dev/events";

    /**
     * @return The port bound
     */
    int port();

    /**
     * @return How many browsers are connected
     */
    int connections();

    /**
     * Asks every connected browser to reload.
     *
     * @param path The path that changed, {@code /} for the page itself
     * @param liveCss Whether the path is a stylesheet the browser can swap without reloading the page
     */
    void reload(String path, boolean liveCss);

    /**
     * Serves the files of a directory under a path prefix, on the loopback address and the port of the server, so a page
     * such as a test report has an {@code http://localhost} address rather than a {@code file:} one. A request for a
     * directory serves its {@code index.html}, and the HTML served carries the client script, so the page reloads when
     * asked. Nothing outside the directory is served. Mounting a prefix again replaces the directory.
     *
     * @param prefix The path prefix, such as {@code /reports/tests/}
     * @param directory The directory
     * @return The address of the mount, such as {@code http://localhost:35729/reports/tests/}
     * @since 5.3.0
     */
    String serve(String prefix, Path directory);

    /**
     * Stops serving a prefix.
     *
     * @param prefix The prefix given to {@link #serve}
     * @since 5.3.0
     */
    void unserve(String prefix);

    /**
     * Sends JSON to every page connected to the event channel with the topic, such as the events of a test run to a
     * report that shows them as they happen.
     *
     * @param topic The topic
     * @param json The message, a JSON document
     * @since 5.3.0
     */
    void publish(String topic, String json);

    /**
     * @param topic The topic
     * @return How many pages listen to the topic
     * @since 5.3.0
     */
    int subscribers(String topic);

    /**
     * Stops the server and disconnects the browsers.
     */
    @Override
    void close();

    /**
     * The script tag a page includes to connect to a server on the given port.
     *
     * @param port The port
     * @return The tag
     */
    static String scriptTag(int port) {
        return "<script src=\"http://localhost:" + port + SCRIPT_PATH + "?snipver=1\" async></script>";
    }
}
