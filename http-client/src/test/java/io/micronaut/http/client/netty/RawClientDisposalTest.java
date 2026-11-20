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
package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawClientDisposalTest {

    @Test
    void aDisposedRawClientIsClosedAndReplaced() {
        try (ApplicationContext context = ApplicationContext.run()) {
            RawHttpClientRegistry registry = context.getBean(RawHttpClientRegistry.class);
            HttpVersionSelection version = HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1);
            RawHttpClient first = registry.getRawClient(version, "http://upstream.example:8080", null);
            assertSame(first, registry.getRawClient(version, "http://upstream.example:8080", null));
            registry.disposeRawClient(version, "http://upstream.example:8080", null);
            assertFalse(((DefaultHttpClient) first).isRunning());
            RawHttpClient second = registry.getRawClient(version, "http://upstream.example:8080", null);
            assertNotSame(first, second);
            assertTrue(((DefaultHttpClient) second).isRunning());
        }
    }
}
