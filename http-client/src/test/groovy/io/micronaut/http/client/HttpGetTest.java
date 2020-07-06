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
package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.reactivex.Flowable;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Test;
import io.micronaut.core.type.Argument;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.List;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpGetTest {



    @Test
    public void testSimpleGet() {
        ApplicationContext applicationContext = ApplicationContext.run();
        EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        HttpClient client = new DefaultHttpClient(server.getURL());
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/simple"), String.class
        ));
        HttpResponse<String> response = flowable.blockingFirst();

        Assert.assertEquals(HttpStatus.OK, response.getStatus());
        Optional<String> body = response.getBody(String.class);
        assertTrue(body.isPresent());
        assertEquals("success", body.get());

        client.stop();
        applicationContext.stop();
    }

    @Test
    public void testGetPojoList() {
        ApplicationContext applicationContext = ApplicationContext.run();
        EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        HttpClient client = applicationContext.createBean(HttpClient.class, server.getURL());

        Flowable<HttpResponse<List>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/pojoList"), Argument.of(List.class, HttpGetSpec.Book.class)
        ));
        HttpResponse<List> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());
        Optional<List> body = response.getBody();
        assertTrue(body.isPresent());

        List<HttpGetSpec.Book> list = body.get();
        assertEquals(1, list.size());
        assertTrue(list.get(0) instanceof HttpGetSpec.Book);

        client.stop();
        applicationContext.stop();
    }
}
