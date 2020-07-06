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
package io.micronaut.docs.datavalidation.pogo;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class EmailControllerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "datavalidationpogo"));
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    //tag::pojovalidated[]
    public void testPoJoValidation() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            Email email = new Email();
            email.subject = "Hi";
            email.recipient = "";
            client.toBlocking().exchange(HttpRequest.POST("/email/send", email));
        });
        HttpResponse response = e.getResponse();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        Email email = new Email();
        email.subject = "Hi";
        email.recipient = "me@micronaut.example";
        response = client.toBlocking().exchange(HttpRequest.POST("/email/send", email));

        assertEquals(HttpStatus.OK, response.getStatus());
    }
    //end::pojovalidated[]
}
