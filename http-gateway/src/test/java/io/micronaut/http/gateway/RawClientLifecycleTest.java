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
package io.micronaut.http.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The client of a URI is closed with the last route that uses it; the client of a service stays.
 */
class RawClientLifecycleTest {

    @Test
    void theClientOfAUriIsClosedWithItsLastRoute() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProxyHandlerFactory factory = context.getBean(ProxyHandlerFactory.class);
            RawHttpClientRegistry registry = context.getBean(RawHttpClientRegistry.class);
            GatewayRoute a = GatewayRoute.builder("a").uri("http://upstream.example:8080").build();
            GatewayRoute b = GatewayRoute.builder("b").uri("http://upstream.example:8080").build();
            HttpVersionSelection version = factory.httpVersion(a);
            factory.rawClient(a, version);
            factory.rawClient(b, version);
            RawHttpClient shared = registry.getRawClient(version, "http://upstream.example:8080", null);
            factory.releaseRawClient(a, version);
            assertSame(shared, registry.getRawClient(version, "http://upstream.example:8080", null), "b still uses it");
            factory.releaseRawClient(b, version);
            assertNotSame(shared, registry.getRawClient(version, "http://upstream.example:8080", null), "closed with the last route");
        }
    }

    @Test
    void aReplacedHandlerGivesBackItsClients() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProxyHandlerFactory factory = context.getBean(ProxyHandlerFactory.class);
            RawHttpClientRegistry registry = context.getBean(RawHttpClientRegistry.class);
            GatewayRoute route = GatewayRoute.builder("r").uri("http://other.example:8080").build();
            DefaultProxyHandler handler = (DefaultProxyHandler) factory.create(route);
            HttpVersionSelection version = factory.httpVersion(route);
            factory.rawClient(route, version);
            RawHttpClient before = registry.getRawClient(version, "http://other.example:8080", null);
            // the handler took no client yet: closing it gives back nothing
            handler.close();
            assertSame(before, registry.getRawClient(version, "http://other.example:8080", null));
            factory.releaseRawClient(route, version);
            assertNotSame(before, registry.getRawClient(version, "http://other.example:8080", null));
        }
    }
}
