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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * The attributes of a request that is sent with options, or proxied, reach the client filters,
 * like those of a request that is sent without options.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawRequestAttributesTest {
    static final String SPEC_NAME = "RawRequestAttributesTest";
    static final String TENANT_ATTRIBUTE = "raw-attributes.tenant";

    @Test
    void attributesReachClientFiltersWithoutOptions() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            Assertions.assertEquals("acme", body(Mono.from(client.exchange(request(server), null, null)).block()));
        }
    }

    @Test
    void attributesReachClientFiltersWithDefaultOptions() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            Assertions.assertEquals("acme", body(Mono.from(client.exchange(request(server), null, null, RawRequestOptions.getDefault())).block()));
        }
    }

    @Test
    void attributesReachClientFiltersWithProxyOptions() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            Assertions.assertEquals("acme", body(Mono.from(client.exchange(request(server), null, null, RawRequestOptions.proxy())).block()));
        }
    }

    @Test
    void attributesReachClientFiltersOfAsyncExchangesWithOptions() throws Exception {
        try (ServerUnderTest server = server();
             AsyncRawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class).toAsyncRaw()) {
            Assertions.assertEquals("acme", body(client.exchange(request(server), null, RawRequestOptions.getDefault()).toCompletableFuture().get(10, TimeUnit.SECONDS)));
            Assertions.assertEquals("acme", body(client.exchange(request(server), null, RawRequestOptions.proxy()).toCompletableFuture().get(10, TimeUnit.SECONDS)));
        }
    }

    @Test
    void attributesReachClientFiltersOfProxiedRequests() throws Exception {
        try (ServerUnderTest server = server()) {
            ProxyHttpClient client = server.getApplicationContext().getBean(ProxyHttpClient.class);
            Assertions.assertEquals("acme", body(Mono.from(client.proxy(request(server))).block()));
            Assertions.assertEquals("acme", body(Mono.from(client.proxy(request(server), ProxyRequestOptions.getDefault())).block()));
        }
    }

    private static MutableHttpRequest<?> request(ServerUnderTest server) {
        MutableHttpRequest<?> request = HttpRequest.GET(server.getURL().get() + "/raw-attributes/tenant");
        request.setAttribute(TENANT_ATTRIBUTE, "acme");
        return request;
    }

    private static String body(@Nullable HttpResponse<?> response) throws Exception {
        Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response);
        try (ByteBodyHttpResponse<?> byteBodyResponse = (ByteBodyHttpResponse<?>) response) {
            Assertions.assertEquals(200, byteBodyResponse.code());
            return byteBodyResponse.byteBody().buffer().get().toString(StandardCharsets.UTF_8);
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @ClientFilter("/raw-attributes/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TenantFilter {
        @RequestFilter
        void tenant(MutableHttpRequest<?> request) {
            request.getAttribute(TENANT_ATTRIBUTE, String.class).ifPresent(tenant -> request.header("X-Tenant", tenant));
        }
    }

    @Controller("/raw-attributes")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TenantController {
        @Get(value = "/tenant", produces = MediaType.TEXT_PLAIN)
        String tenant(@Header("X-Tenant") @Nullable String tenant) {
            return tenant == null ? "none" : tenant;
        }
    }
}
