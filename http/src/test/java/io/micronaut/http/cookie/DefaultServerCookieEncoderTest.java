package io.micronaut.http.cookie;

import io.micronaut.http.simple.cookies.SimpleCookie;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void serverCookieEncoderIsDefaultServerCookieEncoder() {
        assertInstanceOf(DefaultServerCookieEncoder.class, ServerCookieEncoder.INSTANCE);
    }

    @Test
    void encodeRejectsHeaderInjection() {
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder();
        // ';' would inject an additional cookie attribute (e.g. Domain/Path/Secure)
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(Cookie.of("SID", "a; Domain=evil.example")));
        // CR/LF would split the Set-Cookie header into additional response headers (CWE-113)
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(Cookie.of("SID", "a\r\nSet-Cookie: admin=true")));
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(Cookie.of("SID", "a").path("/\r\nLocation: https://evil.example")));
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(Cookie.of("SID", "a").domain("evil.example; Secure")));
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(cookie("SID", "a").path("/" + (char) 0x7f)));
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(cookie("SID", "a").domain("example.com" + (char) 0xe9)));
    }

    @Test
    void encodeRejectsInvalidStrictCookieNames() {
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder();
        for (char separator : "()<>@,;:\\\"/[]?={} \t".toCharArray()) {
            Cookie cookie = new SimpleCookie("S" + separator + "ID", "value");
            assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(cookie));
        }
        Cookie cookie = new SimpleCookie("S" + (char) 0 + "ID", "value");
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(cookie));
    }

    @Test
    void encodeRejectsInvalidStrictCookieValues() {
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder();
        for (String value : new String[] {"a b", "a,b", "a\\b", "a\"b", "caf" + (char) 0xe9}) {
            assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(new SimpleCookie("SID", value)));
        }
    }

    @Test
    void encodeAllowsBalancedWrappingQuotes() {
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder();
        assertEquals("SID=\"31d4d96e407aad42\"", cookieEncoder.encode(cookie("SID", "\"31d4d96e407aad42\"")).get(0));
        assertEquals("SID=\"\"", cookieEncoder.encode(cookie("SID", "\"\"")).get(0));
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(new SimpleCookie("SID", "\"unbalanced")));
    }

    @Test
    void encodeEmitsValidatedComponents() {
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder();
        assertEquals("SID=value; Path=/safe; Domain=example.com", cookieEncoder.encode(new ChangingServerCookie()).get(0));
    }

    private static Cookie cookie(String name, String value) {
        return new SimpleCookie(name, value).maxAge(Cookie.UNDEFINED_MAX_AGE);
    }

    private static final class ChangingServerCookie extends SimpleCookie {
        private int nameReads;
        private int valueReads;
        private int pathReads;
        private int domainReads;

        private ChangingServerCookie() {
            super("SID", "value");
            maxAge(Cookie.UNDEFINED_MAX_AGE);
            path("/safe");
            domain("example.com");
        }

        @Override
        public String getName() {
            return nameReads++ == 0 ? "SID" : "bad;name";
        }

        @Override
        public String getValue() {
            return valueReads++ == 0 ? "value" : "bad;value";
        }

        @Override
        public String getPath() {
            return pathReads++ == 0 ? "/safe" : "/bad;path";
        }

        @Override
        public String getDomain() {
            return domainReads++ == 0 ? "example.com" : "evil.example; Secure";
        }
    }

    private static String expires(Long maxAgeSeconds) {
        ZoneId gmtZone = ZoneId.of("GMT");
        LocalDateTime localDateTime = LocalDateTime.now(gmtZone).plusSeconds(maxAgeSeconds);
        ZonedDateTime gmtDateTime = ZonedDateTime.of(localDateTime, gmtZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
        return gmtDateTime.format(formatter);
    }
}
