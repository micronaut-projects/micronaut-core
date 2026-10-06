package io.micronaut.http.client.sse

import io.micronaut.http.client.exceptions.ContentLengthExceededException
import io.micronaut.http.sse.Event
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

class EventStreamDecoderSpec extends Specification {

    void "lines end with a line feed, a carriage return and a line feed, or a carriage return"() {
        expect:
        data(decode("data: a\n\ndata: b\r\n\r\ndata: c\r\rdata: d\n\n")) == ["a", "b", "c", "d"]
    }

    void "a carriage return and a line feed split between two pieces end one line"() {
        expect:
        data(decode("data: a\r", "\n\r", "\n", "data: b\r", "\n", "\r\n")) == ["a", "b"]
    }

    void "two carriage returns split between two pieces end two lines"() {
        expect:
        data(decode("data: a\r", "\rdata: b\n\n")) == ["a", "b"]
    }

    void "pieces of any size decode the same events"() {
        given:
        String stream = "﻿id: 1\r\nevent: greeting\r\ndata: hello\r\ndata: world\r\n\r\n: comment\ndata: {\"a\":1}\nretry: 1000\n\ndata: last\r\r"

        when:
        List<Event<byte[]>> whole = decode(stream)
        List<Event<byte[]>> byteByByte = decode(stream.getBytes(StandardCharsets.UTF_8).collect { [it] as byte[] } as byte[][])

        then:
        data(whole) == ["hello\nworld", "{\"a\":1}", "last"]
        describe(byteByByte) == describe(whole)
    }

    void "the data lines of an event are joined with a line feed"() {
        expect:
        data(decode("data: first\ndata:second\ndata\ndata:  indented\n\n")) == ["first\nsecond\n\n indented"]
    }

    void "a leading byte order mark is skipped, also when split between pieces"() {
        given:
        byte[] bom = [0xEF, 0xBB, 0xBF] as byte[]

        expect:
        data(decode((bom.toList() + "data: a\n\n".getBytes(StandardCharsets.UTF_8).toList()) as byte[])) == ["a"]
        data(decode([bom[0]] as byte[], [bom[1], bom[2]] as byte[], "data: a\n\n".getBytes(StandardCharsets.UTF_8))) == ["a"]
    }

    void "comments and unknown fields are ignored"() {
        expect:
        data(decode(": a comment\nunknown: field\ndata: a\n\n")) == ["a"]
    }

    void "the last event id carries over, an id with a null character is ignored"() {
        when:
        List<Event<byte[]>> events = decode("id: 1\ndata: a\n\ndata: b\n\nid: 2\u0000\ndata: c\n\nid: 3\ndata: d\n\n")

        then:
        events*.id == ["1", "1", "1", "3"]
    }

    void "the name and the retry apply to one event, a retry that is not a number is ignored"() {
        when:
        List<Event<byte[]>> events = decode("event: first\nretry: 1500\ndata: a\n\ndata: b\n\nretry: soon\ndata: c\n\n")

        then:
        events*.name == ["first", null, null]
        events*.retry == [Duration.ofMillis(1500), null, null]
    }

    void "an event without data is not dispatched"() {
        expect:
        decode("event: empty\nid: 1\n\ndata: a\n\n").size() == 1
    }

    void "an event that is not terminated by a blank line is not dispatched"() {
        expect:
        decode("data: a\n\ndata: unterminated\n").size() == 1
    }

    void "a line longer than the limit fails"() {
        given:
        EventStreamDecoder decoder = new EventStreamDecoder(8)

        when:
        decoder.decode("data: 0".getBytes(StandardCharsets.UTF_8))
        decoder.decode("123456789".getBytes(StandardCharsets.UTF_8))

        then:
        thrown(ContentLengthExceededException)
    }

    void "event data larger than the limit fails"() {
        given:
        // each line is within the limit, the data of the event is not
        EventStreamDecoder decoder = new EventStreamDecoder(11)

        when:
        decoder.decode("data: 0123\ndata: 4567\ndata: 89ab\n\n".getBytes(StandardCharsets.UTF_8))

        then:
        thrown(ContentLengthExceededException)
    }

    private static List<Event<byte[]>> decode(String... pieces) {
        return decode(pieces.collect { it.getBytes(StandardCharsets.UTF_8) } as byte[][])
    }

    private static List<Event<byte[]>> decode(byte[]... pieces) {
        EventStreamDecoder decoder = new EventStreamDecoder(1024)
        List<Event<byte[]>> events = []
        for (byte[] piece : pieces) {
            events.addAll(decoder.decode(piece))
        }
        return events
    }

    private static List<String> data(List<Event<byte[]>> events) {
        return events.collect { new String(it.data, StandardCharsets.UTF_8) }
    }

    private static List<List<Object>> describe(List<Event<byte[]>> events) {
        return events.collect { [new String(it.data, StandardCharsets.UTF_8), it.id, it.name, it.retry] }
    }
}
