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
package io.micronaut.reactive.rxjava.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClient;
import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

public class RxHttpClientConfiguration {
    private static RxHttpClientFactory clientFactory = null;

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @Internal
    static RxHttpClient createClient(@Nullable URL url) {
        RxHttpClientFactory clientFactory = getReactiveHttpClientFactory();
        return clientFactory.createClient(url);
    }

    private static RxHttpClientFactory resolveClientFactory() {
        final Iterator<RxHttpClientFactory> i = ServiceLoader.load(RxHttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No ReactorHttpClientFactory present on classpath, cannot create HTTP client");
    }

    private static RxHttpClientFactory getReactiveHttpClientFactory() {
        RxHttpClientFactory clientFactory = RxHttpClientConfiguration.clientFactory;
        if (clientFactory == null) {
            synchronized (RxHttpClientConfiguration.class) { // double check
                clientFactory = RxHttpClientConfiguration.clientFactory;
                if (clientFactory == null) {
                    clientFactory = resolveClientFactory();
                    RxHttpClientConfiguration.clientFactory = clientFactory;
                }
            }
        }
        return clientFactory;
    }

}
