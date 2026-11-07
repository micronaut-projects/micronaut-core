package io.micronaut.http.cookie;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.http.HttpMethod;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CookieUtilsTest {
    @Test
    void verifyCookieSizeWithinLimits() {
        final Cookie cookie = Cookie.of("name", "value");
        final String cookieEncoded = ServerCookieEncoder.INSTANCE.encode(cookie).get(0);
        assertDoesNotThrow(() -> CookieUtils.verifyCookieSize(cookie, cookieEncoded));
    }

    @Test
    void verifyCookieSizeWithinLimitsCustom() {
        final Cookie cookie = Cookie.of("name", "value");
        final String cookieEncoded = ServerCookieEncoder.INSTANCE.encode(cookie).get(0);
        assertDoesNotThrow(() -> CookieUtils.verifyCookieSize(cookie, cookieEncoded, 100));
    }

    @Test
    void verifyCookieSizeOutOfLimits() {
        StringBuilder sb = new StringBuilder();
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length <= 4096) {
            sb.append('a');
        }
        final Cookie cookie = Cookie.of("name", sb.toString());
        final String cookieEncoded = ServerCookieEncoder.INSTANCE.encode(cookie).get(0);
        CookieSizeExceededException ex = assertThrows(CookieSizeExceededException.class,
            () -> CookieUtils.verifyCookieSize(cookie, cookieEncoded));
        assertEquals("name", ex.getCookieName());
        assertEquals(4096, ex.getMaxSize());
        assertTrue(ex.getSize() > 4096);
    }

    @Test
    void verifyCookieSizeOutOfLimitsCustom() {
        StringBuilder sb = new StringBuilder();
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length <= 1000) {
            sb.append('a');
        }
        final Cookie cookie = Cookie.of("name", sb.toString());
        final String cookieEncoded = ServerCookieEncoder.INSTANCE.encode(cookie).get(0);
        CookieSizeExceededException ex = assertThrows(CookieSizeExceededException.class,
            () -> CookieUtils.verifyCookieSize(cookie, cookieEncoded, 1000));
        assertEquals("name", ex.getCookieName());
        assertEquals(1000, ex.getMaxSize());
        assertTrue(ex.getSize() > 1000);
    }

    @Test
    void setRequestCookieMergesIntoOneHeaderAndReplacesByName() {
        MutableHttpRequest<?> request = new SimpleHttpRequest<>(HttpMethod.GET, "/", null);
        // as a request received from a client could hold them: two Cookie headers
        request.getHeaders().add(HttpHeaders.COOKIE, "a=1; b=2");
        request.getHeaders().add(HttpHeaders.COOKIE, "c=4");

        CookieUtils.setRequestCookie(request.getHeaders(), "a", "a=3");
        CookieUtils.setRequestCookie(request.getHeaders(), "d", "d=5");

        assertEquals(List.of("a=3; b=2; c=4; d=5"), request.getHeaders().getAll(HttpHeaders.COOKIE));
    }

    @Test
    void setRequestCookieWithoutCookieHeader() {
        MutableHttpRequest<?> request = new SimpleHttpRequest<>(HttpMethod.GET, "/", null);

        CookieUtils.setRequestCookie(request.getHeaders(), "a", "a=1");

        assertEquals(List.of("a=1"), request.getHeaders().getAll(HttpHeaders.COOKIE));
    }
}
