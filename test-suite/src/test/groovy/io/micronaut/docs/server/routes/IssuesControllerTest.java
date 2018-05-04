/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.*;
import io.micronaut.runtime.server.EmbeddedServer;
import static org.junit.Assert.*;
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class IssuesControllerTest {

    // tag::setup[]
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass // <1>
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                    .getApplicationContext()
                    .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass // <1>
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }
    // end::setup[]

    // tag::test[]
    @Test
    public void testIssue() throws Exception {
        String body = client.toBlocking().retrieve("/issues/12"); // <2>
        assertNotNull(body);
        assertEquals( // <3>
                body,
                "Issue # 12!"
        );
    }
    // end::test[]
}
