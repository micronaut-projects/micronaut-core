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
package io.micronaut.docs.server.routes;

// tag::imports[]
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
// end::imports[]

// tag::class[]
public class IssuesControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass // <1>
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                    .getApplicationContext()
                    .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass // <2>
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    @Test
    public void testIssue() throws Exception {
        String body = client.toBlocking().retrieve("/issues/12"); // <3>

        assertNotNull(body);
        assertEquals("Issue # 12!", body); // <4>
    }

    @Test
    public void testShowWithInvalidInteger() {
        HttpClientResponseException e =Assertions.assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange("/issues/hello"));

        assertEquals(400, e.getStatus().getCode()); // <5>
    }

    @Test
    public void testIssueWithoutNumber() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange("/issues/"));

        assertEquals(404, e.getStatus().getCode()); // <6>
    }
}
// end::class[]
