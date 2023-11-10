/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.docs.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PointControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "PointControllerTest"));
        client = server
            .getApplicationContext()
            .createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.stop();
        }
    }

    @Test
    void testJsonWithNoAtBodyEndpoint() {
        HttpRequest<String> httpRequest = HttpRequest
            .POST("/point/no-body-json", "{\"x\":10,\"y\":20}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        HttpResponse<Point> response = client.toBlocking().exchange(httpRequest, Point.class);

        assertResult(response.getBody().orElse(null));
    }

    @Test
    void testFormWithNoAtBodyEndpoint() {
        HttpRequest<String> httpRequest = HttpRequest
            .POST("/point/no-body-form", "x=10&y=20")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
        HttpResponse<Point> response = client.toBlocking().exchange(httpRequest, Point.class);

        assertResult(response.getBody().orElse(null));
    }

    private void assertResult(Point p) {
        assertNotNull(p);
        assertEquals(Integer.valueOf(10), p.getX());
        assertEquals(Integer.valueOf(20), p.getY());
    }
}
