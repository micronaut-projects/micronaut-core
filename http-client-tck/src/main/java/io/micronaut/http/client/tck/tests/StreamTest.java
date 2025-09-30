/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.StreamingHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static io.micronaut.http.tck.TestScenario.asserts;

class StreamTest {
    private static final String SPEC_NAME = "StreamTest";
    private static final int REPS = 1000;

    @Test
    void dataStreamRelease() throws IOException {
        asserts(SPEC_NAME,
            Map.of(),
            HttpRequest.GET("/encoding/bytes"),
            (server, request) -> {
                try (StreamingHttpClient client = server.getApplicationContext().createBean(StreamingHttpClient.class, server.getURL().orElseThrow())) {
                    List<byte[]> arrays = Flux.from(client.dataStream(request))
                        .map(bb -> {
                            byte[] arr = bb.toByteArray();
                            if (bb instanceof ReferenceCounted rc) {
                                rc.release();
                            }
                            return arr;
                        })
                        .collectList().block();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    for (byte[] array : arrays) {
                        baos.writeBytes(array);
                    }
                    Assertions.assertEquals("foo".repeat(REPS), baos.toString(StandardCharsets.UTF_8));
                }
            });
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/encoding")
    static class StreamingController {

        @Get("/bytes")
        Flux<byte[]> bytes() {
            return Flux.fromStream(IntStream.range(0, REPS).mapToObj(i -> "foo".getBytes(StandardCharsets.UTF_8)));
        }
    }
}
