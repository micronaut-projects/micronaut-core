package io.micronaut.http.netty.body

import tools.jackson.core.json.JsonFactory
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import io.micronaut.json.JsonSyntaxException
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
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
        def counter = new JsonCounter()
        counter.feed(Unpooled.wrappedBuffer(input.getBytes(StandardCharsets.UTF_8)))
        then:
        !counter.isBuffering()
        counter.pollFlushedRegion() == new JsonCounter.BufferRegion(0, input.length())

        where:
        input << ['{}', '[]', '["foo]"]', '{"foo[":"{bar"}', '{"foo":{"bar":"baz"}}']
    }

    static List<byte[]> splitUtf8(byte[] s, boolean unwrapTopLevelArray = false, boolean skipOptional = false) {
        def parts = []
        def counter = new JsonCounter()
        if (unwrapTopLevelArray) {
            counter.unwrapTopLevelArray()
        }
        int sectionStart = 0
        def buf = Unpooled.wrappedBuffer(s)
        def bias = counter.position()
        while (buf.isReadable()) {
            counter.feed(buf)
            def flushedRegion = counter.pollFlushedRegion()
            if (flushedRegion != null) {
                parts.add(ByteBufUtil.getBytes(buf.slice((int) (flushedRegion.start() - bias), (int) (flushedRegion.end() - flushedRegion.start()))))
            }
        }
        if (counter.isBuffering()) {
            def start = (int) (counter.bufferStart() - bias)
            parts.add(ByteBufUtil.getBytes(buf.slice(start, buf.writerIndex() - start)))
        }
        counter.noMoreInput()
        return parts
    }

    /**
     * Same as {@link #splitUtf8}, but feeds the input in {@code chunkSize} byte chunks, the way
     * {@code JsonChunkedProcessor} does for a chunked request body.
     */
    static List<byte[]> splitUtf8Chunked(byte[] s, boolean unwrapTopLevelArray = false, int chunkSize = 1) {
        def parts = []
        def counter = new JsonCounter()
        if (unwrapTopLevelArray) {
            counter.unwrapTopLevelArray()
        }
        def pending = new ByteArrayOutputStream()
        for (int offset = 0; offset < s.length; offset += chunkSize) {
            def buf = Unpooled.wrappedBuffer(Arrays.copyOfRange(s, offset, (int) Math.min(offset + chunkSize, s.length)))
            def initialPosition = counter.position()
            def bias = initialPosition - buf.readerIndex()
            while (buf.isReadable()) {
                counter.feed(buf)
                def flushedRegion = counter.pollFlushedRegion()
                if (flushedRegion != null) {
                    def start = Math.max(initialPosition, flushedRegion.start())
                    def bytes = ByteBufUtil.getBytes(buf.slice((int) (start - bias), (int) (flushedRegion.end() - start)))
                    pending.write(bytes, 0, bytes.length)
                    parts.add(pending.toByteArray())
                    pending.reset()
                }
            }
            if (counter.isBuffering()) {
                def start = (int) (Math.max(initialPosition, counter.bufferStart()) - bias)
                def bytes = ByteBufUtil.getBytes(buf.slice(start, buf.writerIndex() - start))
                pending.write(bytes, 0, bytes.length)
            }
        }
        counter.noMoreInput()
        if (pending.size() > 0) {
            parts.add(pending.toByteArray())
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
        '"foo" 42' | ['"foo"', '42']
        //'42"foo"'  | ['42', '"foo"'] unsupported
        '42 "foo"' | ['42', '"foo"']
        //'42{}'     | ['42', '{}'] unsupported
        '42 {}'    | ['42', '{}']
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

        when: "the input is fed one byte at a time"
        def chunkedParts = splitUtf8Chunked(stream.getBytes(StandardCharsets.UTF_8), true)
                .collect { new String(it, StandardCharsets.UTF_8) }
        then:
        chunkedParts == expectedParts

        where:
        stream          | expectedParts
        '{}'            | ['{}']
        '[{}]'          | ['{}']
        '[42]'          | ['42']
        '{}{}'          | ['{}', '{}']
        '{}[]'          | ['{}', '[]']
        '"foo"42'       | ['"foo"', '42']
        '"foo" 42'      | ['"foo"', '42']
        '42 "foo"'      | ['42', '"foo"']
        '42 {}'         | ['42', '{}']
        '42 []'         | ['42', '[]']
        '[1,"foo" ,{}]' | ['1', '"foo"', '{}']
        '[6 ,6]'        | ['6', '6']
    }

    def 'illegal inputs'(byte[] input) {
        when:
        splitUtf8(input, false, false)
        then:
        thrown JsonSyntaxException
        when:
        splitUtf8(input, true, false)
        then:
        thrown JsonSyntaxException
        when:
        splitUtf8(input, false, true)
        then:
        thrown JsonSyntaxException
        when:
        splitUtf8(input, true, true)
        then:
        thrown JsonSyntaxException

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
        thrown JsonSyntaxException
        when:
        splitUtf8(input, true, true)
        then:
        thrown JsonSyntaxException
        when: "the input is fed one byte at a time"
        splitUtf8Chunked(input, true)
        then:
        thrown JsonSyntaxException

        where:
        input << [
                '[] 42',
                '[{}] "foo"',
                '[{}] true',
                // missing element separator
                '[1 2]',
                '[{}{}]',
                '["foo""bar"]',
                // stray element separator
                '[1,,2]',
                '[,1]',
                // unterminated array
                '[',
                '[1,2',
                '[{}',
                '[{"foo":',
        ]
    }
}
