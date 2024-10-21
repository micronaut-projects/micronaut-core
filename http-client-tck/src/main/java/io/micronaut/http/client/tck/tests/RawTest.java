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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawTest {
    public static final String SPEC_NAME = "RawTest";

    private static final byte[] LONG_PAYLOAD;

    static {
        LONG_PAYLOAD = new byte[1024 * 1024];
        ThreadLocalRandom.current().nextBytes(LONG_PAYLOAD);
    }

    @Test
    public void getLong() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET(server.getURL().get() + "/raw/get-long"), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            Assertions.assertArrayEquals(
                LONG_PAYLOAD,
                response.byteBody().buffer().get().toByteArray()
            );
        }
    }

    @Test
    public void getLongInputStream() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET(server.getURL().get() + "/raw/get-long"), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            try (InputStream is = response.byteBody().toInputStream()) {
                Assertions.assertArrayEquals(
                    LONG_PAYLOAD,
                    is.readAllBytes()
                );
            }
        }
    }

    @Test
    public void echoLong() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(client.exchange(
                     HttpRequest.POST(server.getURL().get() + "/raw/echo", null),
                     AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, LONG_PAYLOAD),
                     null
                 ))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            Assertions.assertArrayEquals(
                LONG_PAYLOAD,
                response.byteBody().buffer().get().toByteArray()
            );
        }
    }

    @Test
    public void httpClientFactory() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = RawHttpClient.create(null);
             ByteBodyHttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET(server.getURL().get() + "/raw/get-long"), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            Assertions.assertArrayEquals(
                LONG_PAYLOAD,
                response.byteBody().buffer().get().toByteArray()
            );
        }
    }

    @Test
    public void filterRequestReplaceResponse() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            HttpResponse<?> response = Mono.from(client.exchange(
                    HttpRequest.POST(server.getURL().get() + "/raw/filter-request-replace-response", null),
                    AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, "foo".getBytes(StandardCharsets.UTF_8)),
                    null
                ))
                .block();

            Assertions.assertFalse(response instanceof ByteBodyHttpResponse<?>);
            Assertions.assertEquals("Replaced response. Request body: foo", response.getBody(String.class).get());
        }
    }

    @Test
    public void filterReplaceResponse() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            HttpResponse<?> response = Mono.from(client.exchange(
                    HttpRequest.GET(server.getURL().get() + "/raw/filter-replace-response"),
                    null,
                    null
                ))
                .block();

            Assertions.assertFalse(response instanceof ByteBodyHttpResponse<?>);
            Assertions.assertEquals("Replaced response. Response body: bar", response.getBody(String.class).get());
        }
    }

    @Test
    public void redirect() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET(server.getURL().get() + "/raw/redirect-from"), null, null))
                 .cast(ByteBodyHttpResponse.class)
                 .block()) {

            Assertions.assertEquals(
                "redirect successful",
                response.byteBody().buffer().get().toString(StandardCharsets.UTF_8)
            );
        }
    }

    @Controller("/raw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawController {
        @Get("/get-long")
        public InputStream getLong() {
            return new ByteArrayInputStream(LONG_PAYLOAD);
        }

        @Post("/echo")
        public InputStream echo(@Body InputStream body) throws IOException {
            return body;
        }

        @Get("/filter-replace-response")
        public String filterReplaceResponse() {
            return "bar";
        }

        @Get("/redirect-from")
        public HttpResponse<?> redirectFrom() {
            return HttpResponse.redirect(URI.create("/raw/redirect-to"));
        }

        @Get("/redirect-to")
        public String redirectTo() {
            return "redirect successful";
        }
    }

    @ClientFilter("/raw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawFilter {
        @RequestFilter("/filter-request-replace-response")
        public HttpResponse<?> filterRequestReplaceResponse(HttpRequest<?> request, @Body String body) throws Exception {
            // @Body happens to work, but this should very much be considered experimental (and will only work for the raw client)
            return HttpResponse.ok("Replaced response. Request body: " + body);
        }

        @ResponseFilter("/filter-replace-response")
        @ExecuteOn(TaskExecutors.BLOCKING)
        public HttpResponse<?> filterReplaceResponse(HttpResponse<?> response) throws Exception {
            try (ByteBodyHttpResponse<?> r = (ByteBodyHttpResponse<?>) response) {
                return HttpResponse.ok("Replaced response. Response body: " + r.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }
}
