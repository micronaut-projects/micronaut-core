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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A load balanced raw client reports its exchanges to the load balancer: with outlier detection,
 * an instance that keeps failing stops being selected.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class LoadBalancerFeedbackTest {
    static final String SPEC_NAME = "LoadBalancerFeedbackTest";
    private static final int REQUESTS = 12;

    @Test
    void failingInstanceIsEjected() throws Exception {
        try (ServerUnderTest server = server()) {
            URI dead = closedPort();
            URI alive = server.getURL().get().toURI();
            try (ApplicationContext clients = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME + "-client",
                "micronaut.http.services.flaky.urls", List.of(dead.toString(), alive.toString()),
                "micronaut.http.services.flaky.outlier-detection.enabled", true,
                "micronaut.http.services.flaky.outlier-detection.consecutive-failures", 2,
                "micronaut.http.services.flaky.outlier-detection.base-ejection-time", "1m"
            )); RawHttpClient client = rawClient(clients, "flaky")) {
                List<UnprocessedRequestException> failures = new ArrayList<>();
                StringBuilder sequence = new StringBuilder();
                for (int i = 0; i < REQUESTS; i++) {
                    try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET("/load-balancer-feedback/ok"))) {
                        Assertions.assertEquals(200, response.code());
                        sequence.append("alive ");
                    } catch (UnprocessedRequestException e) {
                        Assertions.assertEquals(UnprocessedRequestException.Reason.CONNECT, e.getReason());
                        Assertions.assertEquals(dead.getPort(), e.getServiceInstance().orElseThrow().getPort());
                        sequence.append("dead ");
                        Assertions.assertTrue(i < REQUESTS / 2, "The dead instance was still selected for request " + i + ": " + sequence);
                        failures.add(e);
                    }
                }
                Assertions.assertFalse(failures.isEmpty(), "The dead instance was never selected");
                Assertions.assertTrue(failures.size() <= 2, "The dead instance was selected " + failures.size() + " times, it should have been ejected after 2 failures");
            }
        }
    }

    private static RawHttpClient rawClient(ApplicationContext context, String serviceId) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(serviceId));
        return context.getBean(RawHttpClientRegistry.class).getRawClient(HttpVersionSelection.forClientConfiguration(configuration), serviceId, null);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request) {
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, RawRequestOptions.proxy())).block();
        return Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response);
    }

    private static URI closedPort() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        return URI.create("http://127.0.0.1:" + port);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Controller("/load-balancer-feedback")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OkController {
        @Get(value = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
        }
    }
}
