package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultClientCookieEncoderTest {
    @Test
    void clientCookieEncoderIsDefaultClientCookieEncoder() {
        assertInstanceOf(DefaultClientCookieEncoder.class, ClientCookieEncoder.INSTANCE);
    }

    @Test
    void clientCookieEncoding() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        Cookie cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com");
        assertEquals("SID=31d4d96e407aad42", cookieEncoder.encode(cookie));
    }

    @Test
    void encodeRejectsHeaderInjection() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        // ';' would inject an additional cookie pair into the Cookie header
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(Cookie.of("SID", "a; admin=true")));
        // CR/LF would split the request headers (CWE-113)
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(Cookie.of("SID", "a\r\nX-Evil: 1")));
    }
}
