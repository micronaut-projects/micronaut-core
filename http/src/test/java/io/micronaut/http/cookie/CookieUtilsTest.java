package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CookieUtilsTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CookieUtilsTest.class);

    @Test
    void cookieLog() {
        MemoryAppender appender = new MemoryAppender();
        Logger l = (Logger) LoggerFactory.getLogger("io.micronaut.http.cookie.CookieUtilsTest");
        l.addAppender(appender);
        appender.start();

        Cookie cookie = Cookie.of("name", "value");
        String cookieEncoded = ServerCookieEncoder.INSTANCE.encode(cookie).get(0);
        CookieUtils.logCookieByteLimit(LOG, cookie, cookieEncoded);

        assertNotNull(appender.getEvents());
        assertTrue(appender.getEvents().isEmpty());

        StringBuilder sb = new StringBuilder();
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length <= 4096) {
            sb.append('a');
        }
        cookie = Cookie.of("name", sb.toString());
        cookieEncoded = ServerCookieEncoder.INSTANCE.encode(cookie).get(0);
        CookieUtils.logCookieByteLimit(LOG, cookie, cookieEncoded);

        assertNotNull(appender.getEvents());
        assertFalse(appender.getEvents().isEmpty());
        assertTrue(appender.getEvents().get(0).endsWith(" greater than limit 4096"));
    }
}
