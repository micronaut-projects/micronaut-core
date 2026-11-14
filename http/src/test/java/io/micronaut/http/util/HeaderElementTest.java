package io.micronaut.http.util;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderElementTest {

    @Test
    void parseValueAndParameters() {
        HeaderElement e = HeaderElement.parse("form-data; Name=\"file\"; filename=\"a;b,\\\"c\\\".txt\" ; flag");
        assertEquals("form-data", e.value());
        assertEquals("file", e.parameter("name"));
        assertEquals("file", e.parameter("NAME"));
        assertEquals("a;b,\"c\".txt", e.parameter("filename"));
        assertEquals("", e.parameter("flag"));
        assertNull(e.parameter("missing"));
        assertEquals(List.of("name", "filename", "flag"), List.copyOf(e.parameters().keySet()));
    }

    @Test
    void firstRepeatedParameterWins() {
        assertEquals("1", HeaderElement.parse("x; a=1; A=2").parameter("a"));
    }

    @Test
    void parseListRespectsQuotesAndAngleBrackets() {
        List<HeaderElement> list = HeaderElement.parseList("<http://a/b;c,d>; rel=\"next, last\", , text/plain;q=0.5,");
        assertEquals(2, list.size());
        assertEquals("<http://a/b;c,d>", list.get(0).value());
        assertEquals("next, last", list.get(0).parameter("rel"));
        assertEquals("text/plain", list.get(1).value());
        assertEquals(0.5, list.get(1).quality());
    }

    @Test
    void parseListOfSeveralValues() {
        List<HeaderElement> list = HeaderElement.parseList(List.of("a, b", "c"));
        assertEquals(List.of("a", "b", "c"), list.stream().map(HeaderElement::value).toList());
    }

    @Test
    void quality() {
        assertEquals(1.0, HeaderElement.parse("a").quality());
        assertEquals(0.25, HeaderElement.parse("a;q=0.25").quality());
        assertEquals(0.0, HeaderElement.parse("a;q=x").quality());
        assertEquals(0.0, HeaderElement.parse("a;q=1.5").quality());
    }

    @Test
    void qualityOrderedListIsStableAndDropsZero() {
        List<HeaderElement> list = HeaderElement.parseQualityOrderedList(List.of("a;q=0.5, b, c;q=0, d;q=0.5", "e"));
        assertEquals(List.of("b", "e", "a", "d"), list.stream().map(HeaderElement::value).toList());
    }

    @Test
    void splitList() {
        assertEquals(List.of("a", "\"b,c\"", "<d,e>"), HeaderElement.splitList(" a ,, \"b,c\" , <d,e>,"));
        assertEquals(List.of(), HeaderElement.splitList(""));
    }

    @Test
    void quoteAndUnquote() {
        assertEquals("token", HeaderElement.quoteIfNeeded("token"));
        assertEquals("\"\"", HeaderElement.quoteIfNeeded(""));
        assertEquals("\"a b\\\"c\\\\\"", HeaderElement.quoteIfNeeded("a b\"c\\"));
        assertEquals("a b\"c\\", HeaderElement.unquote("\"a b\\\"c\\\\\""));
        assertEquals("plain", HeaderElement.unquote(" plain "));
        assertTrue(HeaderElement.isToken("abc-1.2"));
        assertFalse(HeaderElement.isToken("a/b"));
    }

    @Test
    void parametersAreImmutable() {
        HeaderElement e = new HeaderElement("v", Map.of("a", "1"));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> e.parameters().put("b", "2"));
    }

    @Test
    void acceptLanguages() {
        SimpleHttpHeaders headers = new SimpleHttpHeaders();
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "fr;q=0.5, en-US, *;q=0.1");
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "de;q=0, es");
        assertEquals(List.of(Locale.US, Locale.forLanguageTag("es"), Locale.FRENCH), headers.acceptLanguages());
        assertEquals(List.of(), new SimpleHttpHeaders().acceptLanguages());
    }
}
