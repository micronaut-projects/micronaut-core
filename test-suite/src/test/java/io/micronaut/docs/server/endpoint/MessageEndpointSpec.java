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
package io.micronaut.docs.server.endpoint;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MessageEndpointSpec {

    @Test
    public void testReadMessageEndpoint() {
        Map<String, Object> map = new HashMap<>();
        map.put("endpoints.message.enabled", true);
        map.put("spec.name", MessageEndpointSpec.class.getSimpleName());
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());
        HttpResponse<String> response = rxClient.exchange("/message", String.class).blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("default message", response.body());

        server.close();
    }

    @Test
    public void testWriteMessageEndpoint() {
        Map<String, Object> map = new HashMap<>();
        map.put("endpoints.message.enabled", true);
        map.put("spec.name", MessageEndpointSpec.class.getSimpleName());
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());
        Map<String, Object> map2 = new HashMap<>();
        map2.put("newMessage", "A new message");
        HttpResponse<String> response = rxClient.exchange(HttpRequest.POST("/message", map2)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED), String.class).blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Message updated", response.body());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getContentType().get());

        response = rxClient.exchange("/message", String.class).blockingFirst();

        assertEquals("A new message", response.body());

        server.close();
    }

    @Test
    public void testDeleteMessageEndpoint() {
        Map<String, Object> map = new HashMap<>();
        map.put("endpoints.message.enabled", true);
        map.put("spec.name", MessageEndpointSpec.class.getSimpleName());
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());
        HttpResponse<String> response = rxClient.exchange(HttpRequest.DELETE("/message"), String.class).blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Message deleted", response.body());

        try {
            rxClient.exchange("/message", String.class).blockingFirst();
        } catch (HttpClientResponseException e) {
            assertEquals(404, e.getStatus().getCode());
        } catch (Exception e) {
            fail("Wrong exception thrown");
        } finally {
            server.close();
        }
    }
}
