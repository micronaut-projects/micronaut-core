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
package io.micronaut.docs.server.intro;

// tag::imports[]
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
// end::imports[]
@Property(name = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@MicronautTest
public class HelloControllerSpec {
    @Inject
    EmbeddedServer server; // <1>

    @Inject
    @Client("/")
    HttpClient client; // <2>

    @Test
    void testHelloWorldResponse() {
        String response = client.toBlocking() // <3>
                .retrieve(HttpRequest.GET("/hello"));
        assertEquals("Hello World", response); //) <4>
    }
}
//end::class[]
