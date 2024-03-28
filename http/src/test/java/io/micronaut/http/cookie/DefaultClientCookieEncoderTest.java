package io.micronaut.http.cookie;

import io.micronaut.core.order.Ordered;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultClientCookieEncoderTest {
    @Test
    void clientCookieEncoding() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        Cookie cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com");
        assertEquals("SID=31d4d96e407aad42", cookieEncoder.encode(cookie));
    }

    @Test
    void orderIsLowestPrecedence() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        assertEquals(Ordered.LOWEST_PRECEDENCE, cookieEncoder.getOrder());
    }
}
