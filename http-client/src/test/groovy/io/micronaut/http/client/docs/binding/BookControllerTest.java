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
package io.micronaut.http.client.docs.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.POST;

/**
 * @author graemerocher
 * @since 1.0
 */
public class BookControllerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testPostInvalidFormData() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());

        // tag::postform[]
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", "The Stand");
        data.put("pages", "notnumber");
        data.put("url", "noturl");

        final HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            Flux.from(client.exchange(
                    POST("/binding/book", data)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                    Book.class
            )).blockFirst();
        });

        String message = ((Map) ((List) ((Map) exception.getResponse().getBody(Map.class).get().get("_embedded")).get("errors")).get(0)).get("message").toString();
        Assertions.assertTrue(message.startsWith("Failed to convert argument [book] for value [null] due to: Cannot deserialize value of type `int` from String \"notnumber\""));

        embeddedServer.stop();
        client.stop();
    }
}
