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
package org.particleframework.http.client;

import io.reactivex.Flowable;
import static org.junit.Assert.*;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.runtime.server.EmbeddedServer;

import java.util.Collections;
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
                HttpRequest.get("/get/simple"), String.class
        ));
        HttpResponse<String> response = flowable.blockingFirst();

        assertEquals(response.getStatus(), HttpStatus.OK);
        Optional<String> body = response.getBody(String.class);
        assertTrue(body.isPresent());
        assertEquals(body.get(), "success");

        applicationContext.stop();
    }

    @Test
    public void testGetPojoList() {
        ApplicationContext applicationContext = ApplicationContext.run();
        EmbeddedServer server = applicationContext.getBean(EmbeddedServer.class).start();

        HttpClient client = applicationContext.createBean(HttpClient.class, Collections.singletonMap("url", server.getURL()));

        Flowable<HttpResponse<List>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.get("/get/pojoList"), Argument.of(List.class, HttpGetSpec.Book.class)
        ));
        HttpResponse<List> response = flowable.blockingFirst();

        assertEquals(response.getStatus(), HttpStatus.OK);
        Optional<List> body = response.getBody();
        assertTrue(body.isPresent());

        List<HttpGetSpec.Book> list = body.get();
        assertEquals(list.size(), 1);
        assertTrue(list.get(0) instanceof HttpGetSpec.Book);

        applicationContext.stop();
    }
}
