/*
 * Copyright 2018 original authors
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
package io.micronaut.http.client.docs.basics;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.Assert.*;

import io.reactivex.Flowable;
import org.junit.Test;

/**
 * @author graemerocher
 * @since 1.0
 */
public class HelloControllerTest {

    @Test
    public void testSimpleRetrieve() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::simple[]
        String result = client.toBlocking().retrieve("/hello/John");

        assertEquals(
                "Hello John",
                result
        );
        // end::simple[]

        embeddedServer.stop();
        client.stop();
    }

    @Test
    public void testRetrieveWithHeaders() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::headers[]
        Flowable<String> response = client.retrieve(
                GET("/hello/John")
                .header("X-My-Header", "SomeValue")
        );
        // end::headers[]

        assertEquals(
                "Hello John",
                response.blockingFirst()
        );

        embeddedServer.stop();
        client.stop();
    }
}
