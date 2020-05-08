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
package io.micronaut.http.client.docs.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.reactivex.Flowable;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.LinkedHashMap;
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
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL());

        // tag::postform[]
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", "The Stand");
        data.put("pages", "notnumber");
        data.put("url", "noturl");
        Flowable<HttpResponse<Book>> call = client.exchange(
                POST("/binding/book", data)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                Book.class
        );
        // end::postform[]

        thrown.expect(HttpClientResponseException.class);
        thrown.expectMessage(CoreMatchers.startsWith("Failed to convert argument [book] for value [null] due to: Cannot deserialize value of type `int` from String \"notnumber\""));

        HttpResponse<Book> response = call.blockingFirst();

        embeddedServer.stop();
        client.stop();
    }
}
