package io.micronaut.http.cookie;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultServerCookieEncoderTest {

    @Test
    void encodeCookie() {
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder();
        Cookie cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com");
        assertEquals("SID=31d4d96e407aad42; Path=/; Domain=example.com", cookieEncoder.encode(cookie).get(0));

        cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com").sameSite(SameSite.Strict);
        assertEquals("SID=31d4d96e407aad42; Path=/; Domain=example.com; SameSite=Strict", cookieEncoder.encode(cookie).get(0));

        cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").secure().httpOnly();
        assertEquals("SID=31d4d96e407aad42; Path=/; Secure; HttpOnly", cookieEncoder.encode(cookie).get(0));

        long maxAge = 2592000;
        String expected = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge);
        String expected2 = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge + 1); // To prevent flakiness
        cookie = Cookie.of("id", "a3fWa").maxAge(maxAge);
        String result = cookieEncoder.encode(cookie).get(0);
        assertTrue(expected.equals(result) || expected2.equals(result));
    }

    private static String expires(Long maxAgeSeconds) {
        ZoneId gmtZone = ZoneId.of("GMT");
        LocalDateTime localDateTime = LocalDateTime.now(gmtZone).plusSeconds(maxAgeSeconds);
        ZonedDateTime gmtDateTime = ZonedDateTime.of(localDateTime, gmtZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
        return gmtDateTime.format(formatter);
    }
}
