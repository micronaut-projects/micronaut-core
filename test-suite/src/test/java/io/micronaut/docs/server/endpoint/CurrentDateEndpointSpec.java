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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class CurrentDateEndpointSpec {

    @Test
    public void testReadCustomDateEndpoint() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        HttpResponse<String> response = rxClient.exchange("/date", String.class).blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());

        server.close();
    }

    @Test
    public void testReadCustomDateEndpointWithArgument() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        HttpResponse<String> response = rxClient.exchange("/date/current_date_is", String.class).blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertTrue(response.body().startsWith("current_date_is: "));

        server.close();
    }

    // issue https://github.com/micronaut-projects/micronaut-core/issues/883
    @Test
    public void testReadWithProduces() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        HttpResponse<String> response = rxClient.exchange("/date/current_date_is", String.class).blockingFirst();

        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getContentType().get());

        server.close();
    }

    @Test
    public void testWriteCustomDateEndpoint() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());
        Date originalDate, resetDate;
        Map<String, Object> map = new HashMap<>();

        HttpResponse<String> response = rxClient.exchange("/date", String.class).blockingFirst();
        originalDate = new Date(Long.parseLong(response.body()));

        response = rxClient.exchange(HttpRequest.POST("/date", map), String.class).blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Current date reset", response.body());

        response = rxClient.exchange("/date", String.class).blockingFirst();
        resetDate = new Date(Long.parseLong(response.body()));

        assert resetDate.getTime() > originalDate.getTime();

        server.close();
    }

    @Test
    public void testDisableEndpoint() {
        Map<String, Object> map = new HashMap<>();
        map.put("custom.date.enabled", false);
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        try {
            rxClient.exchange("/date", String.class).blockingFirst();
        } catch (HttpClientResponseException ex) {
            assertEquals(HttpStatus.NOT_FOUND.getCode(), ex.getResponse().code());
        }

        server.close();
    }
}
