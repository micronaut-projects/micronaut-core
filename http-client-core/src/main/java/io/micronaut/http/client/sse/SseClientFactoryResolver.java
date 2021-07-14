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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Resolves an {@link SseClientFactory} from a service loader.
 *
 * @author James Kleeh
 * @since 3.0.0
 */
@Internal
final class SseClientFactoryResolver {

    private static SseClientFactory factory;

    static SseClientFactory getFactory() {
        if (factory == null) {
            synchronized (SseClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static SseClientFactory resolveClientFactory() {
        final Iterator<SseClientFactory> i = ServiceLoader.load(SseClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No SseClientFactory present on classpath, cannot create sse client");
    }
}
