package io.micronaut.docs.client;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.jdk.DefaultJdkHttpClient;
import io.micronaut.http.client.jdk.JdkHttpClient;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@Property(name = "spec.name", value = "MultiClientSpec")
class MultiClientSpec {

    @Inject
    @Client("/")
    HttpClient nettyClient;

    @Inject
    @Client("/")
    JdkHttpClient jdkClient;

    @Test
    void testMultiClient() {
        assertEquals(DefaultHttpClient.class, nettyClient.getClass());
        assertEquals(DefaultJdkHttpClient.class, jdkClient.getClass());
        assertEquals("ok bar", nettyClient.toBlocking().retrieve(getRequest()));
        assertEquals("ok bar", jdkClient.toBlocking().retrieve(getRequest()));
    }

    private static MutableHttpRequest<Object> getRequest() {
        return HttpRequest.GET("/multi-client").cookie(Cookie.of("foo", "bar"));
    }

    @Controller
    @Requires(property = "spec.name", value = "MultiClientSpec")
    static class MultiClientController {

        @Get(uri = "/multi-client", produces = MediaType.TEXT_PLAIN)
        String multiClient(@CookieValue("foo") String foo) {
            return "ok " + foo;
        }
    }
}
