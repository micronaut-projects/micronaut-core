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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Resolves an {@link HttpClientFactory} from a service loader.
 *
 * @author James Kleeh
 * @since 3.0.0
 */
@Internal
final class HttpClientFactoryResolver {

    private static HttpClientFactory factory;

    static HttpClientFactory getFactory() {
        if (factory == null) {
            synchronized (HttpClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static HttpClientFactory resolveClientFactory() {
        final Iterator<HttpClientFactory> i = ServiceLoader.load(HttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No HttpClientFactory present on classpath, cannot create client");
    }
}
