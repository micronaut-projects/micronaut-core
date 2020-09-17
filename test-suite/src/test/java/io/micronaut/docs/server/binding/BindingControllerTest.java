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
package io.micronaut.docs.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BindingControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    @Test
    public void testCookieBinding() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieName").cookie(Cookie.of("myCookie", "cookie value")));

        assertNotNull(body);
        assertEquals("cookie value", body);

        body = client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieInferred").cookie(Cookie.of("myCookie", "cookie value")));

        assertNotNull(body);
        assertEquals("cookie value", body);
    }


    @Test
    public void testCookiesBinding() {

        HashSet<Cookie> cookies = new HashSet<>();
        cookies.add(Cookie.of("myCookieA", "cookie A value"));
        cookies.add(Cookie.of("myCookieB", "cookie B value"));

        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieMultiple").cookies(cookies));

        assertNotNull(body);
        assertEquals("[\"cookie A value\",\"cookie B value\"]", body);
    }

    @Test
    public void testHeaderBinding() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/headerName").header("Content-Type", "test"));

        assertNotNull(body);
        assertEquals("test", body);

        body = client.toBlocking().retrieve(HttpRequest.GET("/binding/headerInferred").header("Content-Type", "test"));

        assertNotNull(body);
        assertEquals("test", body);

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(HttpRequest.GET("/binding/headerNullable")));

        assertEquals(ex.getResponse().getStatus(), HttpStatus.NOT_FOUND);
    }

    @Test
    public void testHeaderDateBinding() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/date").header("date", "Tue, 3 Jun 2008 11:05:30 GMT"));

        assertNotNull(body);
        assertEquals("2008-06-03T11:05:30Z", body);

        body = client.toBlocking().retrieve(HttpRequest.GET("/binding/dateFormat").header("date", "03/06/2008 11:05:30 AM GMT"));

        assertNotNull(body);
        assertEquals("2008-06-03T11:05:30Z[GMT]", body);
    }
}
