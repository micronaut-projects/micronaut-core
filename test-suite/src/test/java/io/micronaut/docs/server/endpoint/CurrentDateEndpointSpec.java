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
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Test;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class CurrentDateEndpointSpec {

    @Test
    public void testReadCustomDateEndpoint() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        HttpClient rxClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

        HttpResponse<String> response = rxClient.toBlocking().exchange("/date", String.class);

        assertEquals(HttpStatus.OK.getCode(), response.code());

        server.close();
    }

    @Test
    public void testReadCustomDateEndpointWithArgument() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        HttpClient rxClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

        HttpResponse<String> response = rxClient.toBlocking().exchange("/date/current_date_is", String.class);

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertTrue(response.body().startsWith("current_date_is: "));

        server.close();
    }

    // issue https://github.com/micronaut-projects/micronaut-core/issues/883
    @Test
    public void testReadWithProduces() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        HttpClient rxClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

        HttpResponse<String> response = rxClient.toBlocking().exchange("/date/current_date_is", String.class);

        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getContentType().get());

        server.close();
    }

    @Test
    public void testWriteCustomDateEndpoint() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        HttpClient rxClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
        Date originalDate, resetDate;
        Map<String, Object> map = new HashMap<>();

        HttpResponse<String> response = rxClient.toBlocking().exchange("/date", String.class);
        originalDate = new Date(Long.parseLong(response.body()));

        response = rxClient.toBlocking().exchange(HttpRequest.POST("/date", map), String.class);

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Current date reset", response.body());

        response = rxClient.toBlocking().exchange("/date", String.class);
        resetDate = new Date(Long.parseLong(response.body()));

        assert resetDate.getTime() > originalDate.getTime();

        server.close();
    }

    @Test
    public void testDisableEndpoint() {
        Map<String, Object> map = new HashMap<>();
        map.put("custom.date.enabled", false);
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        HttpClient rxClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

        try {
            rxClient.toBlocking().exchange("/date", String.class);
        } catch (HttpClientResponseException ex) {
            assertEquals(HttpStatus.NOT_FOUND.getCode(), ex.getResponse().code());
        }

        server.close();
    }
}
