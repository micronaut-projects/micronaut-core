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
package io.micronaut.runtime.server;

import io.micronaut.runtime.EmbeddedApplication;

import java.net.URI;
import java.net.URL;

/**
 * <p>An EmbeddedServer is a general abstraction to manage the lifecycle of any server implementation within
 * a running Micronaut application.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface EmbeddedServer extends EmbeddedApplication<EmbeddedServer> {

    /**
     * @return The port exposed by the server
     */
    int getPort();

    /**
     * @return The host of the server
     */
    String getHost();

    /**
     * @return The scheme of the server (http/https)
     */
    String getScheme();

    /**
     * @return The full URL to the server
     */
    URL getURL();

    /**
     * @return The full URI to the server
     */
    URI getURI();

    @Override
    default boolean isServer() {
        return true;
    }


    /**
     * Most servers provide a way to block such that the server doesn't exit, however some require the creation of a keep alive thread.
     *
     * @return True if the server should be kept alive.
     */
    default boolean isKeepAlive() {
        return true;
    }
}
