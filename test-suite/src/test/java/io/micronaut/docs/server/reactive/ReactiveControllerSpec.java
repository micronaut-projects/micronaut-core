/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.reactive;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReactiveControllerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
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
    void testReactive() {
        String body = client.toBlocking().retrieve("/reactive/reactor-mono");

        assertEquals("Hello world", body);

        body = client.toBlocking().retrieve("/reactive/reactor-flux");

        assertEquals("[Hello world]", body);

        body = client.toBlocking().retrieve("/reactive/reactor-flux-single");

        assertEquals("Hello world", body);

        body = client.toBlocking().retrieve("/reactive/rxjava2-single");

        assertEquals("Hello world", body);

        body = client.toBlocking().retrieve("/reactive/rxjava2-maybe");

        assertEquals("Hello world", body);

        body = client.toBlocking().retrieve("/reactive/rxjava2-flowable");

        assertEquals("[Hello world]", body);

        body = client.toBlocking().retrieve("/reactive/rxjava2-flowable-single");

        assertEquals("Hello world", body);

        var response = client.toBlocking().exchange("/reactive/rxjava2-flowable-completable");

        assertNull(response.body());

        body = client.toBlocking().retrieve("/reactive/rxjava3-single");

        assertEquals("Hello world", body);

        body = client.toBlocking().retrieve("/reactive/rxjava3-maybe");

        assertEquals("Hello world", body);

        body = client.toBlocking().retrieve("/reactive/rxjava3-flowable");

        assertEquals("[Hello world]", body);

        body = client.toBlocking().retrieve("/reactive/rxjava3-flowable-single");

        assertEquals("Hello world", body);

        response = client.toBlocking().exchange("/reactive/rxjava3-flowable-completable");

        assertNull(response.body());

    }
}
