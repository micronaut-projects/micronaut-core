package io.micronaut.http.server.netty.jackson

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class JsonCounterSpec extends Specification {
    private static final JsonFactory FACTORY = new JsonFactory()

    static List<JsonToken> toTokens(String input) {
        return toTokens(input.getBytes(StandardCharsets.UTF_8))
    }

    static List<JsonToken> toTokens(byte[] input) {
        def list = []
        // try to parse fully
        try (JsonParser parser = FACTORY.createParser(input)) {
            //noinspection GroovyEmptyStatementBody
            while (true) {
                def token = parser.nextToken()
                if (token == null) {
                    break
                }
                list.add(token)
            }
        }
        return list
    }

    def 'compare with jackson'(String input) {
        given:
        // try to parse fully
        toTokens(input)

        when:
        def counter = JsonCounter.create(false)
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            counter.feed(b)
        }
        then:
        counter.depth == 0

        where:
        input << ['{}', '[]', '["foo]"]', '{"foo[":"{bar"}']
    }

    static List<byte[]> splitUtf8(byte[] s, boolean unwrapTopLevelArray = false, boolean skipOptional = false) {
        def parts = []
        def current = new ByteArrayOutputStream()
        def counter = JsonCounter.create(unwrapTopLevelArray)
        int sectionStart = 0
        for (int i = 0; i < s.length; i++) {
            def feedResult = counter.feed(s[i])
            switch (feedResult) {
                case JsonCounter.FeedResult.MAY_SKIP:
                    if (!skipOptional) {
                        current.write(s[i])
                    }
                    break
                case JsonCounter.FeedResult.BUFFER:
                    current.write(s[i])
                    break
                case JsonCounter.FeedResult.FLUSH_AFTER:
                    current.write(s[i])
                    parts.add(current.toByteArray())
                    current.reset()
                    break
                case JsonCounter.FeedResult.FLUSH_BEFORE_AND_SKIP:
                    parts.add(current.toByteArray())
                    current.reset()
                    break
            }
        }
        def remaining = current.toByteArray()
        if (remaining.length != 0) {
            parts.add(remaining)
        }
        return parts
    }

    def 'split compare with jackson'(String stream, List<String> expectedParts) {
        given:
        def fullTokens = toTokens(stream)

        when:
        def parts = splitUtf8(stream.getBytes(StandardCharsets.UTF_8))
                .collect { new String(it, StandardCharsets.UTF_8) }
        then:
        parts == expectedParts
        parts.collectMany { toTokens(it) } == fullTokens

        when:
        def partsWithoutOptional = splitUtf8(stream.getBytes(StandardCharsets.UTF_8), false, true)
                .collect { new String(it, StandardCharsets.UTF_8) }
        then:
        partsWithoutOptional.collectMany { toTokens(it) } == fullTokens

        where:
        stream     | expectedParts
        '{}'       | ['{}']
        '[{}]'     | ['[{}]']
        '[42]'     | ['[42]']
        '{}{}'     | ['{}', '{}']
        '{}[]'     | ['{}', '[]']
        '"foo"42'  | ['"foo"', '42']
        '"foo" 42' | ['"foo"', ' 42']
        //'42"foo"'  | ['42', '"foo"'] unsupported
        '42 "foo"' | ['42 ', '"foo"']
        //'42{}'     | ['42', '{}'] unsupported
        '42 {}'    | ['42 ', '{}']
    }

    def 'split compare with jackson, top level array'(String stream, List<String> expectedParts) {
        given:
        def fullTokens = toTokens(stream)
        if (fullTokens[0] == JsonToken.START_ARRAY) {
            // unwrap top-level array
            assert fullTokens.last() == JsonToken.END_ARRAY
            fullTokens.remove(fullTokens.size() - 1)
            fullTokens.remove(0)
        }

        when:
        def parts = splitUtf8(stream.getBytes(StandardCharsets.UTF_8), true)
                .collect { new String(it, StandardCharsets.UTF_8) }
        then:
        parts == expectedParts
        parts.collectMany { toTokens(it) } == fullTokens

        when:
        def partsWithoutOptional = splitUtf8(stream.getBytes(StandardCharsets.UTF_8), true, true)
                .collect { new String(it, StandardCharsets.UTF_8) }
        then:
        partsWithoutOptional.collectMany { toTokens(it) } == fullTokens

        where:
        stream          | expectedParts
        '{}'            | ['{}']
        '[{}]'          | ['{}']
        '[42]'          | ['42']
        '{}{}'          | ['{}', '{}']
        '{}[]'          | ['{}', '[]']
        '"foo"42'       | ['"foo"', '42']
        '"foo" 42'      | ['"foo"', ' 42']
        '42 "foo"'      | ['42 ', '"foo"']
        '42 {}'         | ['42 ', '{}']
        '42 []'         | ['42 ', '[]']
        '[1,"foo" ,{}]' | ['1', '"foo"', ' {}']
    }

    def 'illegal inputs'(byte[] input) {
        when:
        splitUtf8(input, false, false)
        then:
        thrown IllegalArgumentException
        when:
        splitUtf8(input, true, false)
        then:
        thrown IllegalArgumentException
        when:
        splitUtf8(input, false, true)
        then:
        thrown IllegalArgumentException
        when:
        splitUtf8(input, true, true)
        then:
        thrown IllegalArgumentException

        where:
        input << [
                // byte-order mark
                new byte[]{0xef, 0xbb, 0xbf, 0x7b, 0x09, 0x7d, 0x09, 0x20, 0x7b, 0x09, 0x09, 0x7d},
                // no space after number
                '42"foo"',
                '42{}',
                '42[]',
                // utf-16
                new byte[]{0x22, 0x00, 0x22, 0x5b, 0x22, 0x00},
        ]
    }

    def 'illegal inputs unwrapTopLevelArray'(byte[] input) {
        when:
        splitUtf8(input, true, false)
        then:
        thrown IllegalArgumentException
        when:
        splitUtf8(input, true, true)
        then:
        thrown IllegalArgumentException

        where:
        input << [
                '[] 42',
                '[{}] "foo"',
                '[{}] true',
        ]
    }
}
