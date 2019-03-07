/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs.server.intro;

// tag::imports[]

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

// end::imports[]

// tag::classinit[]
public class HelloControllerSpec {
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        // end::classinit[]
        server = ApplicationContext.run(EmbeddedServer.class,
                new HashMap<String, Object>() {{
                    put("spec.name", HelloControllerSpec.class.getSimpleName());
                    put("spec.lang", "java");
                }}
                , Environment.TEST);
        /*
        // tag::embeddedServer[]
            server = ApplicationContext.run(EmbeddedServer) // <1>
        // end::embeddedServer[]
        */
        // tag::class[]
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());// <2>
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.stop();
        }
    }

    @Test
    public void testHelloWorldResponse() {
        String response = client.toBlocking() // <3>
                .retrieve(HttpRequest.GET("/hello"));
        assertEquals("Hello World", response); //) <4>
    }
}
//end::class[]
