package io.micronaut.http.uri;

import spock.lang.Specification

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry
/**
 * This is forked from Netty. See
 * <a href="https://github.com/netty/netty/blob/4.1/codec-http/src/test/java/io/netty/handler/codec/http/QueryStringDecoderTest.java">
 *   QueryStringDecoderTest.java
 * </a>.
 */
class QueryStringDecoderTest extends Specification {

    void testBasicUris() throws URISyntaxException {
        when:
        QueryStringDecoder d = new QueryStringDecoder(new URI("http://localhost/path"))

        then:
        d.parameters().size() == 0
    }

    void testBasic() {
        QueryStringDecoder d

        when:
        d = new QueryStringDecoder("/foo")

        then:
        d.path() == "/foo"
        d.parameters().size() == 0

        when:
        d = new QueryStringDecoder("/foo%20bar")

        then:
        d.path() == "/foo bar"
        d.parameters().size() == 0

        when:
        d = new QueryStringDecoder("/foo?a=b=c")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 1
        d.parameters().get("a").get(0) == "b=c"

        when:
        d = new QueryStringDecoder("/foo?a=1&a=2")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 2
        d.parameters().get("a").get(0) == "1"
        d.parameters().get("a").get(1) == "2"

        when:
        d = new QueryStringDecoder("/foo%20bar?a=1&a=2")

        then:
        d.path() == "/foo bar"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 2
        d.parameters().get("a").get(0) == "1"
        d.parameters().get("a").get(1) == "2"

        when:
        d = new QueryStringDecoder("/foo?a=&a=2")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 2
        d.parameters().get("a").get(0) == ""
        d.parameters().get("a").get(1) == "2"

        when:
        d = new QueryStringDecoder("/foo?a=1&a=")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 2
        d.parameters().get("a").get(0) == "1"
        d.parameters().get("a").get(1) == ""

        when:
        d = new QueryStringDecoder("/foo?a=1&a=&a=")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 3
        d.parameters().get("a").get(0) == "1"
        d.parameters().get("a").get(1) == ""
        d.parameters().get("a").get(2) == ""

        when:
        d = new QueryStringDecoder("/foo?a=1=&a==2")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("a").size() == 2
        d.parameters().get("a").get(0) == "1="
        d.parameters().get("a").get(1) == "=2"

        when:
        d = new QueryStringDecoder("/foo?abc=1%2023&abc=124%20")

        then:
        d.path() == "/foo"
        d.parameters().size() == 1
        d.parameters().get("abc").size() == 2
        d.parameters().get("abc").get(0) == "1 23"
        d.parameters().get("abc").get(1) == "124 "

        when:
        d = new QueryStringDecoder("/foo?abc=%7E")

        then:
        d.parameters().get("abc").get(0) == "~"
    }

    void testExotic() {
        expect:
        assertQueryString("", "")
        assertQueryString("foo", "foo")
        assertQueryString("foo", "foo?")
        assertQueryString("/foo", "/foo?")
        assertQueryString("/foo", "/foo")
        assertQueryString("?a=", "?a")
        assertQueryString("foo?a=", "foo?a")
        assertQueryString("/foo?a=", "/foo?a")
        assertQueryString("/foo?a=", "/foo?a&")
        assertQueryString("/foo?a=", "/foo?&a")
        assertQueryString("/foo?a=", "/foo?&a&")
        assertQueryString("/foo?a=", "/foo?&=a")
        assertQueryString("/foo?a=", "/foo?=a&")
        assertQueryString("/foo?a=", "/foo?a=&")
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&&c=d")
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&=&c=d")
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&==&c=d")
        assertQueryString("/foo?a=b&c=&x=y", "/foo?a=b&c&x=y")
        assertQueryString("/foo?a=", "/foo?a=")
        assertQueryString("/foo?a=", "/foo?&a=")
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&c=d")
        assertQueryString("/foo?a=1&a=&a=", "/foo?a=1&a&a=")
    }

    void testSemicolon() {
        expect:
        assertQueryString("/foo?a=1;2", "/foo?a=1;2", false)
        // "" should be treated as a normal character, see #8855
        assertQueryString("/foo?a=1;2", "/foo?a=1%3B2", true)
    }

    void testPathSpecific() {
        expect:
        // decode escaped characters
        new QueryStringDecoder("/foo%20bar/?").path() == "/foo bar/"
        new QueryStringDecoder("/foo%0D%0A\\bar/?").path() == "/foo\r\n\\bar/"

        // a 'fragment' after '#' should be cuted (see RFC 3986)
        new QueryStringDecoder("#123").path() == ""
        new QueryStringDecoder("foo?bar#anchor").path() == "foo"
        new QueryStringDecoder("/foo-bar#anchor").path() == "/foo-bar"
        new QueryStringDecoder("/foo-bar#a#b?c=d").path() == "/foo-bar"

        // '+' is not escape ' ' for the path
        new QueryStringDecoder("+").path() == "+"
        new QueryStringDecoder("/foo+bar/?").path() == "/foo+bar/"
        new QueryStringDecoder("/foo++?index.php").path() == "/foo++"
        new QueryStringDecoder("/foo%20+?index.php").path() == "/foo +"
        new QueryStringDecoder("/foo+%20").path() == "/foo+ "
    }

    void testExcludeFragment() {
        expect:
        // a 'fragment' after '#' should be cuted (see RFC 3986)
        new QueryStringDecoder("?a#anchor").parameters().keySet().iterator().next() == "a"
        new QueryStringDecoder("?a=b#anchor").parameters().get("a").get(0) == "b"
        new QueryStringDecoder("?#").parameters().isEmpty()
        new QueryStringDecoder("?#anchor").parameters().isEmpty()
        new QueryStringDecoder("#?a=b#anchor").parameters().isEmpty()
        new QueryStringDecoder("?#a=b#anchor").parameters().isEmpty()
    }

    void testHashDos() {
        when:
        StringBuilder buf = new StringBuilder()
        buf.append('?')
        for (int i = 0; i < 65536; i++) {
            buf.append('k')
            buf.append(i)
            buf.append("=v")
            buf.append(i)
            buf.append('&')
        }

        then:
        new QueryStringDecoder(buf.toString()).parameters().size() == 1024
    }

    void testHasPath() {
        when:
        QueryStringDecoder decoder = new QueryStringDecoder("1=2", false)
        Map<String, List<String>> params = decoder.parameters()

        then:
        decoder.path() == ""

        then:
        params.size() == 1
        params.containsKey("1")
        List<String> param = params.get("1")
        param != null
        param.size() == 1
        param.get(0) == "2"
    }

    void testUrlDecoding() throws Exception {
        when:
        final String caffe = new String(
            // "Caffé" but instead of putting the literal E-acute in the
            // source file, we directly use the UTF-8 encoding so as to
            // not rely on the platform's default encoding (not portable).
            new byte[] {'C', 'a', 'f', 'f', (byte) 0xC3, (byte) 0xA9},
            "UTF-8")
        final String[] tests = [
        // Encoded   ->   Decoded or error message substring
        "",               "",
            "foo",            "foo",
            "f+o",            "f o",
            "f++",            "f  ",
            "fo%",            "unterminated escape sequence at index 2 of: fo%",
            "%42",            "B",
            "%5f",            "_",
            "f%4",            "unterminated escape sequence at index 1 of: f%4",
            "%x2",            "invalid hex byte 'x2' at index 1 of '%x2'",
            "%4x",            "invalid hex byte '4x' at index 1 of '%4x'",
            "Caff%C3%A9",     caffe,
            "случайный праздник",               "случайный праздник",
            "случайный%20праздник",             "случайный праздник",
            "случайный%20праздник%20%E2%98%BA", "случайный праздник ☺",
        ]

        then:
        for (int i = 0; i < tests.length; i += 2) {
            final String encoded = tests[i]
            final String expected = tests[i + 1]
            try {
                final String decoded = QueryStringDecoder.decodeComponent(encoded)
                assert decoded == expected
            } catch (IllegalArgumentException e) {
                assert e.getMessage() == expected
            }
        }
    }

    private static void assertQueryString(String expected, String actual) {
        assertQueryString(expected, actual, false)
    }

    private static void assertQueryString(String expected, String actual, boolean semicolonIsNormalChar) {
        QueryStringDecoder ed = new QueryStringDecoder(expected, StandardCharsets.UTF_8, true,
            1024, semicolonIsNormalChar)
        QueryStringDecoder ad = new QueryStringDecoder(actual, StandardCharsets.UTF_8, true,
            1024, semicolonIsNormalChar)
        assert ad.path() == ed.path()
        assert ad.parameters() == ed.parameters()
    }

    // See #189
    void testURI() {
        when:
        URI uri = URI.create("http://localhost:8080/foo?param1=value1&param2=value2&param3=value3")
        QueryStringDecoder decoder = new QueryStringDecoder(uri)

        then:
        decoder.path() == "/foo"
        decoder.rawPath() == "/foo"
        decoder.rawQuery() == "param1=value1&param2=value2&param3=value3"
        Map<String, List<String>> params =  decoder.parameters()
        params.size() == 3
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator()

        when:
        Entry<String, List<String>> entry = entries.next()

        then:
        entry.getKey() == "param1"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value1"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "param2"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value2"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "param3"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value3"

        !entries.hasNext()
    }

    // See #189
    void testURISlashPath() {
        when:
        URI uri = URI.create("http://localhost:8080/?param1=value1&param2=value2&param3=value3")
        QueryStringDecoder decoder = new QueryStringDecoder(uri)

        then:
        decoder.path() == "/"
        decoder.rawPath() == "/"
        decoder.rawQuery() == "param1=value1&param2=value2&param3=value3"

        Map<String, List<String>> params =  decoder.parameters()
        params.size() == 3
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator()

        when:
        Entry<String, List<String>> entry = entries.next()

        then:
        entry.getKey() == "param1"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value1"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "param2"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value2"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "param3"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value3"

        !entries.hasNext()
    }

    // See #189
    void testURINoPath() {
        when:
        URI uri = URI.create("http://localhost:8080?param1=value1&param2=value2&param3=value3")
        QueryStringDecoder decoder = new QueryStringDecoder(uri)

        then:
        decoder.path() == ""
        decoder.rawPath() == ""
        decoder.rawQuery() == "param1=value1&param2=value2&param3=value3"

        Map<String, List<String>> params =  decoder.parameters()
        params.size() == 3
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator()

        when:
        Entry<String, List<String>> entry = entries.next()

        then:
        entry.getKey() == "param1"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value1"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "param2"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value2"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "param3"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "value3"

        !entries.hasNext()
    }

    // See https://github.com/netty/netty/issues/1833
    void testURI2() {
        when:
        URI uri = URI.create("http://foo.com/images;num=10?query=name;value=123")
        QueryStringDecoder decoder = new QueryStringDecoder(uri)

        then:
        decoder.path() == "/images;num=10"
        decoder.rawPath() == "/images;num=10"
        decoder.rawQuery() == "query=name;value=123"

        Map<String, List<String>> params = decoder.parameters()
        params.size() == 2
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator()

        when:
        Entry<String, List<String>> entry = entries.next()

        then:
        entry.getKey() == "query"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "name"

        when:
        entry = entries.next()

        then:
        entry.getKey() == "value"
        entry.getValue().size() == 1
        entry.getValue().get(0) == "123"

        !entries.hasNext()
    }

    void testEmptyStrings() {
        when:
        QueryStringDecoder pathSlash = new QueryStringDecoder("path/")

        then:
        pathSlash.rawPath() == "path/"
        pathSlash.rawQuery() == ""
        QueryStringDecoder pathQuestion = new QueryStringDecoder("path?")
        pathQuestion.rawPath() == "path"
        pathQuestion.rawQuery() == ""
        QueryStringDecoder empty = new QueryStringDecoder("")
        empty.rawPath() == ""
        empty.rawQuery() == ""
    }

}
