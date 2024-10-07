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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    public void echoLong() throws Exception {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = Mono.from(client.exchange(
                     HttpRequest.POST(server.getURL().get() + "/raw/echo", LONG_PAYLOAD),
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
    }
}
