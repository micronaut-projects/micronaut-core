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
package io.micronaut.http.client;

import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.netty.DefaultHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

/**
 * A client created programmatically can be given a handler registry through the public builder,
 * outside the client's own package.
 */
class BuilderHandlerRegistryTest {

    @Test
    void handlerRegistryFromBuilder() {
        MessageBodyHandlerRegistry registry = MessageBodyHandlerRegistry.EMPTY;
        try (DefaultHttpClient client = DefaultHttpClient.builder()
            .uri(URI.create("http://localhost"))
            .configuration(new DefaultHttpClientConfiguration())
            .handlerRegistry(registry)
            .build()) {
            Assertions.assertSame(registry, client.getHandlerRegistry());
        }
    }
}
