/*
 * Copyright 2017-2024 original authors
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

@Internal
final class RawHttpClientFactoryResolver {
    private static volatile RawHttpClientFactory factory;

    static RawHttpClientFactory getFactory() {
        if (factory == null) {
            synchronized (RawHttpClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static RawHttpClientFactory resolveClientFactory() {
        final Iterator<RawHttpClientFactory> i = ServiceLoader.load(RawHttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No RawHttpClientFactory present on classpath, cannot create client");
    }
}
