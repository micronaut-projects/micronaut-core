package io.micronaut.http.client.sse

import io.micronaut.http.sse.Event
import okhttp3.sse.internal.ServerSentEventReader
import okio.Buffer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A differential test of {@link EventStreamDecoder} against an independent event stream
 * parser, the reader of OkHttp's {@code okhttp-sse}: many generated event streams, with the
 * three line endings mixed, comments, a byte order mark, {@code retry} fields, ids with a null
 * character and unknown fields, decoded whole, byte by byte and in random pieces, must give the
 * events the oracle reads.
 *
 * <p>Where OkHttp's reader departs from the specification, the input of the oracle is the
 * equivalent stream under the specification: the leading byte order mark, which it does not
 * skip, is left out, an {@code id} line that contains a null character, which the
 * specification ignores, is removed, and every line ends with a line feed, since OkHttp ends the
 * value of an {@code id}, {@code event} or {@code retry} line only at a line feed. A last line
 * without a line ending fails OkHttp's reader, where the specification discards it. An empty id or event name is compared as none, as OkHttp
 * reports it. OkHttp reports a retry when it reads it; it is compared as the retry of the next
 * event, and the generator writes {@code retry} and {@code id} lines only into events with
 * data, since OkHttp forgets the id an event without data sets, which the specification
 * keeps.</p>
 */
class EventStreamDecoderOracleSpec extends Specification {

    static final List<String> ENDINGS = ['\n', '\r', '\r\n']

    static class Line {
        String text
        String ending
        boolean nulId

        Line(String text, String ending, boolean nulId = false) {
            this.text = text
            this.ending = ending
            this.nulId = nulId
        }
    }

    static class Stream {
        boolean bom
        List<Line> lines = []

        byte[] decoderBytes() {
            def out = new ByteArrayOutputStream()
            if (bom) {
                out.write([0xEF, 0xBB, 0xBF] as byte[])
            }
            lines.each { out.write((it.text + it.ending).getBytes(StandardCharsets.UTF_8)) }
            return out.toByteArray()
        }

        byte[] oracleBytes() {
            def out = new ByteArrayOutputStream()
            // every line ends with a line feed: OkHttp ends the value of an id, event or retry
            // line only at a line feed
            lines.findAll { !it.nulId }.each { out.write((it.text + '\n').getBytes(StandardCharsets.UTF_8)) }
            return out.toByteArray()
        }
    }

    static String pick(Random random, List<String> values) {
        return values[random.nextInt(values.size())]
    }

    static final List<String> DATA = ['', 'x', ' leading space', 'a:b:c', '{"a":1}', 'ünïcödé ✓', 'data: nested', '  two spaces', 'tab\there']
    static final List<String> NAMES = ['', 'ping', ' spaced ', 'message', 'update:1']
    static final List<String> IDS = ['', '1', 'abc', ' 42', 'id with spaces']
    static final List<String> RETRIES = ['1000', '0', '007', 'abc', '', '12x', '99999999999999999999']
    static final List<String> NOISE = [':', ': ping', ':comment: with colon', 'unknown: field', 'unknown', 'Data: wrong case', 'data :space before colon', 'event', 'retry']

    static Stream generate(Random random) {
        Stream stream = new Stream(bom: random.nextInt(5) == 0)
        int events = random.nextInt(6)
        for (int e = 0; e < events; e++) {
            boolean withData = random.nextInt(4) != 0
            List<Line> lines = []
            int fields = 1 + random.nextInt(5)
            for (int f = 0; f < fields; f++) {
                int kind = random.nextInt(withData ? 7 : 3)
                String ending = pick(random, ENDINGS)
                switch (kind) {
                    case 0:
                        lines << new Line(pick(random, NOISE), ending)
                        break
                    case 1:
                        lines << new Line('event:' + (random.nextBoolean() ? ' ' : '') + pick(random, NAMES), ending)
                        break
                    case 2:
                        lines << new Line(pick(random, NOISE), ending)
                        break
                    case 3:
                        // a bare id resets the id, as an empty one does
                        lines << new Line(random.nextInt(5) == 0 ? 'id' : 'id:' + (random.nextBoolean() ? ' ' : '') + pick(random, IDS), ending)
                        break
                    case 4:
                        // ignored by the specification
                        lines << new Line('id: with\u0000null', ending, true)
                        break
                    case 5:
                        lines << new Line('retry:' + (random.nextBoolean() ? ' ' : '') + pick(random, RETRIES), ending)
                        break
                    default:
                        lines << new Line('data:' + (random.nextBoolean() ? ' ' : '') + pick(random, DATA), ending)
                }
            }
            if (withData) {
                // at least one data line, a bare one at times
                int at = random.nextInt(lines.size() + 1)
                lines.add(at, new Line(random.nextInt(4) == 0 ? 'data' : 'data: ' + pick(random, DATA), pick(random, ENDINGS)))
            }
            stream.lines.addAll(lines)
            boolean last = e == events - 1
            if (!last || random.nextInt(3) != 0) {
                // the blank line that dispatches, more than one at times
                int blanks = 1 + (random.nextInt(4) == 0 ? 1 : 0)
                blanks.times { stream.lines << new Line('', pick(random, ENDINGS)) }
            }
        }
        fixLineEndings(stream)
        return stream
    }

    /**
     * A carriage return followed by a line feed is one line ending: a line that ends with a
     * carriage return may not be followed by a blank line that is a line feed in the stream of
     * the decoder, whose blank line would be lost.
     */
    static void fixLineEndings(Stream stream) {
        List<Line> lines = stream.lines
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines[i]
            if (!line.text.isEmpty() || line.ending != '\n') {
                continue
            }
            Line previous = i > 0 ? lines[i - 1] : null
            if (previous != null && previous.ending == '\r') {
                line.ending = '\r\n'
            }
        }
    }

    static List<List<Object>> oracle(byte[] bytes) {
        List<List<Object>> events = []
        Long[] retry = [null]
        def reader = new ServerSentEventReader(new Buffer().write(bytes), new ServerSentEventReader.Callback() {
            @Override
            void onEvent(String id, String type, String data) {
                events << [data, emptyToNull(id), emptyToNull(type), retry[0]]
                retry[0] = null
            }

            @Override
            void onRetryChange(long timeMs) {
                retry[0] = timeMs
            }
        })
        try {
            while (reader.processNextEvent()) {
            }
        } catch (java.io.EOFException | ArrayIndexOutOfBoundsException ignored) {
            // the last line is not ended: OkHttp fails where the specification discards it
        }
        return events
    }

    static List<List<Object>> decoded(List<Event<byte[]>> events) {
        return events.collect {
            [new String(it.data, StandardCharsets.UTF_8), emptyToNull(it.id), emptyToNull(it.name), it.retry?.toMillis()]
        }
    }

    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value
    }

    static List<Event<byte[]>> decode(byte[] bytes, List<Integer> splits) {
        def decoder = new EventStreamDecoder(Long.MAX_VALUE)
        List<Event<byte[]>> events = []
        int start = 0
        for (int split : splits + [bytes.length]) {
            events.addAll(decoder.decode(bytes, start, split - start))
            start = split
        }
        return events
    }

    def "the decoder reads the events the oracle reads"() {
        given:
        Random random = new Random(seed)
        int compared = 0
        int withEvents = 0

        when:
        for (int i = 0; i < 2000; i++) {
            Stream stream = generate(random)
            byte[] bytes = stream.decoderBytes()
            def expected = oracle(stream.oracleBytes())
            if (!expected.isEmpty()) {
                withEvents++
            }
            String description = new String(bytes, StandardCharsets.UTF_8).replace('\r', '\\r').replace('\n', '\\n\n')

            // whole
            def whole = decoded(decode(bytes, []))
            assert whole == expected, description + '\nDECODED ' + whole + '\nORACLE ' + expected
            // byte by byte
            assert decoded(decode(bytes, (1..<Math.max(bytes.length, 1)).toList().findAll { it < bytes.length })) == expected, description
            // random pieces
            List<Integer> splits = (0..<random.nextInt(6)).collect { bytes.length == 0 ? 0 : random.nextInt(bytes.length + 1) }.sort()
            assert decoded(decode(bytes, splits)) == expected, description
            compared++
        }

        then:
        compared == 2000
        // the generator is not degenerate
        withEvents > 1000

        where:
        seed << [1L, 42L, 20261007L]
    }

    def "known streams"() {
        expect:
        decoded(decode(input.getBytes(StandardCharsets.UTF_8), [])) == oracle(input.getBytes(StandardCharsets.UTF_8))

        where:
        input << [
            'data: a\n\n',
            ': ping\n\ndata: a\n\n',
            'id: 1\ndata: a\n\ndata: b\n\n',
            'event:  spaced \ndata: a\n\n',
            'data\n\n',
            'data: a\rdata: b\r\r',
            'data: a\n\n\n\ndata: b\n\n',
            'retry: 1000\ndata: a\n\n',
            'data: a',
        ]
    }
}
