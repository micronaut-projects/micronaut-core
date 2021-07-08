/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.StreamingHttpClientFactory;

import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Resolves a {@link WebSocketClientFactory} from a service loader.
 *
 * @author James Kleeh
 * @since 3.0.0
 */
@Internal
class WebSocketClientFactoryResolver {

    private static WebSocketClientFactory factory;

    @NonNull
    static WebSocketClient createClient(URL url) {
        return getFactory().createWebSocketClient(url);
    }

    @NonNull
    static WebSocketClient createClient(URL url, HttpClientConfiguration configuration) {
        return getFactory().createWebSocketClient(url, configuration);
    }

    private static WebSocketClientFactory getFactory() {
        if (factory == null) {
            synchronized (WebSocketClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static WebSocketClientFactory resolveClientFactory() {
        final Iterator<WebSocketClientFactory> i = ServiceLoader.load(WebSocketClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No HttpClientFactory present on classpath, cannot create client");
    }
}
