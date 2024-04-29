package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import java.net.HttpCookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieHttpCookieAdapterTest {

    @Test
    void testAdapter() {
        Cookie cookie = new CookieHttpCookieAdapter(new HttpCookie("SID", "31d4d96e407aad42"));
        assertEquals("SID", cookie.getName());
        assertEquals("31d4d96e407aad42", cookie.getValue());
        assertNull(cookie.getPath());
        assertNull(cookie.getDomain());
        assertFalse(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
        assertTrue(cookie.getSameSite().isEmpty());
        assertEquals(Cookie.UNDEFINED_MAX_AGE, cookie.getMaxAge());

        cookie = cookie.value("bar")
                .httpOnly()
                .secure()
                .sameSite(SameSite.Strict)
                .path("/foo")
                .domain("micronaut.io");
        assertEquals("bar", cookie.getValue());
        assertEquals("/foo", cookie.getPath());
        assertEquals("micronaut.io", cookie.getDomain());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertFalse(cookie.getSameSite().isEmpty());
        assertEquals(SameSite.Strict, cookie.getSameSite().get());
    }
}
