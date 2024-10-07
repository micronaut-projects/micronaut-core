package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultServerCookieDecoderTest {
    @Test
    void serverCookieDecoderIsDefaultServerCookieDecoder() {
        assertInstanceOf(DefaultServerCookieDecoder.class, ServerCookieDecoder.INSTANCE);
    }

    @Test
    void testCookieDecoding() {
        String header = "SID=31d4d96e407aad42; Path=/; Domain=example.com";
        ServerCookieDecoder decoder = new DefaultServerCookieDecoder();
        List<Cookie> cookies = decoder.decode(header);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        assertEquals("SID", cookie.getName());
        assertEquals("31d4d96e407aad42", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertEquals("example.com", cookie.getDomain());
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
        assertTrue(cookie.getSameSite().isEmpty());

        header = "SID=31d4d96e407aad42; Path=/; Secure; HttpOnly";

        cookies = decoder.decode(header);
        assertNotNull(cookies);
        assertEquals(1, cookies.size());
        cookie = cookies.get(0);
        assertEquals("SID", cookie.getName());
        assertEquals("31d4d96e407aad42", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertNull(cookie.getDomain());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertTrue(cookie.getSameSite().isEmpty());
    }
}
