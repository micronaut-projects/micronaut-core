package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
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
}
