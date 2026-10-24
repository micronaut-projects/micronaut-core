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
package io.micronaut.docs.server.filters.bodybuffer;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "spec.name", value = "ChecksumFilterSpec")
@MicronautTest
class ChecksumFilterSpec {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void theFilterReadsTheBodyAndTheControllerReadsItAgain() throws Exception {
        String body = "x".repeat(256 * 1024);
        String response = client.toBlocking().retrieve(HttpRequest.POST("/checksum", body)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header("X-Content-SHA256", sha256(body)));
        assertEquals("Received " + body.length() + " characters", response);
    }

    @Test
    void aMismatchIsRejected() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(HttpRequest.POST("/checksum", "hello")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Content-SHA256", "wrong")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
