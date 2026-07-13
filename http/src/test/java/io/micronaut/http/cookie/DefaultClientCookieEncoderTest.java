package io.micronaut.http.cookie;

import io.micronaut.http.simple.cookies.SimpleCookie;
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

    @Test
    void encodeRejectsInvalidStrictCookieNames() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        for (char separator : "()<>@,;:\\\"/[]?={} \t".toCharArray()) {
            Cookie cookie = new SimpleCookie("S" + separator + "ID", "value");
            assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(cookie));
        }
        Cookie cookie = new SimpleCookie("S" + (char) 0 + "ID", "value");
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(cookie));
    }

    @Test
    void encodeRejectsInvalidStrictCookieValues() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        for (String value : new String[] {"a b", "a,b", "a\\b", "a\"b", "caf" + (char) 0xe9}) {
            assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(new SimpleCookie("SID", value)));
        }
    }

    @Test
    void encodeAllowsBalancedWrappingQuotes() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        assertEquals("SID=\"31d4d96e407aad42\"", cookieEncoder.encode(new SimpleCookie("SID", "\"31d4d96e407aad42\"")));
        assertEquals("SID=\"\"", cookieEncoder.encode(new SimpleCookie("SID", "\"\"")));
        assertThrows(IllegalArgumentException.class, () -> cookieEncoder.encode(new SimpleCookie("SID", "\"unbalanced")));
    }

    @Test
    void encodeEmitsValidatedNameAndValue() {
        ClientCookieEncoder cookieEncoder = new DefaultClientCookieEncoder();
        assertEquals("SID=value", cookieEncoder.encode(new ChangingClientCookie()));
    }

    private static final class ChangingClientCookie extends SimpleCookie {
        private int nameReads;
        private int valueReads;

        private ChangingClientCookie() {
            super("SID", "value");
        }

        @Override
        public String getName() {
            return nameReads++ == 0 ? "SID" : "bad;name";
        }

        @Override
        public String getValue() {
            return valueReads++ == 0 ? "value" : "bad;value";
        }
    }
}
