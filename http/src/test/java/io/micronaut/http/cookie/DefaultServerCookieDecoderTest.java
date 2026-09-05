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
        // A Cookie request header is a list of name/value pairs; Path/Domain are ordinary cookies
        // here, not attributes of the preceding cookie (RFC 6265 section 4.2.1).
        ServerCookieDecoder decoder = new DefaultServerCookieDecoder();
        List<Cookie> cookies = decoder.decode("SID=31d4d96e407aad42; Path=/; Domain=example.com");
        assertNotNull(cookies);
        assertEquals(3, cookies.size());
        assertEquals("SID", cookies.get(0).getName());
        assertEquals("31d4d96e407aad42", cookies.get(0).getValue());
        assertNull(cookies.get(0).getPath());
        assertNull(cookies.get(0).getDomain());
        assertEquals("Path", cookies.get(1).getName());
        assertEquals("/", cookies.get(1).getValue());
        assertEquals("Domain", cookies.get(2).getName());
        assertEquals("example.com", cookies.get(2).getValue());

        // Attribute-only pairs without a value are not cookie-pairs and are dropped.
        cookies = decoder.decode("SID=31d4d96e407aad42; Path=/; Secure; HttpOnly");
        assertEquals(2, cookies.size());
        assertEquals("SID", cookies.get(0).getName());
        assertEquals("Path", cookies.get(1).getName());
    }

    @Test
    void multipleCookiesAreAllDecoded() {
        List<Cookie> cookies = new DefaultServerCookieDecoder().decode("SID=abc; JSESSIONID=xyz; foo=bar");
        assertEquals(3, cookies.size());
        assertEquals("SID", cookies.get(0).getName());
        assertEquals("abc", cookies.get(0).getValue());
        assertEquals("JSESSIONID", cookies.get(1).getName());
        assertEquals("xyz", cookies.get(1).getValue());
        assertEquals("foo", cookies.get(2).getName());
        assertEquals("bar", cookies.get(2).getValue());
    }

    @Test
    void malformedPairsAreSkippedInsteadOfThrowing() {
        ServerCookieDecoder decoder = new DefaultServerCookieDecoder();
        assertEquals(List.of(), decoder.decode("foo"));
        assertEquals("a", decoder.decode("=noname; a=1").get(0).getName());
        assertEquals(3, decoder.decode("a=1;;b=2; ;c=3").size());
    }

    @Test
    void quotedValuesAreUnwrapped() {
        List<Cookie> cookies = new DefaultServerCookieDecoder().decode("SID=\"quoted value\"; theme=dark");
        assertEquals(2, cookies.size());
        assertEquals("quoted value", cookies.get(0).getValue());
        assertEquals("dark", cookies.get(1).getValue());
    }

    @Test
    void unbalancedOpeningQuoteDropsThePair() {
        // Netty's ServerCookieDecoder.LAX drops a value that starts with a double quote but never
        // closes it; both server decoders must agree on this input.
        ServerCookieDecoder decoder = new DefaultServerCookieDecoder();
        List<Cookie> cookies = decoder.decode("a=\"unterminated; b=2");
        assertEquals(1, cookies.size());
        assertEquals("b", cookies.get(0).getName());
        assertEquals("2", cookies.get(0).getValue());

        assertEquals(List.of(), decoder.decode("a=\""));
        assertEquals("", decoder.decode("a=\"\"").get(0).getValue());
    }
}
