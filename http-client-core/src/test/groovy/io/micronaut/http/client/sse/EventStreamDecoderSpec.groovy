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

    void "a data field without a value dispatches an event with empty data"() {
        when:
        List<Event<byte[]>> events = decode("event: empty\n\ndata\n\n")

        then:
        data(events) == [""]
        events*.name == [null]
    }

    void "an id without data carries over, a retry too large for a number is ignored"() {
        when:
        List<Event<byte[]>> events = decode("id: 1\n\ndata: a\n\nretry: 99999999999999999999\ndata: b\n\n")

        then:
        events*.id == ["1", "1"]
        events*.retry == [null, null]
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

    void "the events before a line larger than the limit are handed out before the failure"() {
        given:
        EventStreamDecoder decoder = new EventStreamDecoder(16)
        List<Event<byte[]>> events = []
        byte[] piece = "data: a\n\ndata: b\n\ndata: 0123456789abcdef\n".getBytes(StandardCharsets.UTF_8)

        when:
        decoder.decode(piece, 0, piece.length, events::add)

        then:
        thrown(ContentLengthExceededException)
        data(events) == ["a", "b"]
    }

    void "a retry-only block sets the reconnection time of the state without an event"() {
        given:
        EventStreamState state = new EventStreamState()
        EventStreamDecoder decoder = new EventStreamDecoder(1024, state)

        when:
        List<Event<byte[]>> events = decoder.decode("retry: 50\n\n".getBytes(StandardCharsets.UTF_8))

        then:
        events.isEmpty()
        state.reconnectionTime == Duration.ofMillis(50)
        state.lastEventId == null

        when:
        events = decoder.decode("data: hi\n\n".getBytes(StandardCharsets.UTF_8))

        then:
        describe(events) == [["hi", null, null, null]]
        state.reconnectionTime == Duration.ofMillis(50)
    }

    void "the reconnection time is set as soon as the retry field is read"() {
        given:
        EventStreamState state = new EventStreamState()
        EventStreamDecoder decoder = new EventStreamDecoder(1024, state)

        when:
        decoder.decode("retry: 70\nretry: x\n".getBytes(StandardCharsets.UTF_8))

        then:
        state.reconnectionTime == Duration.ofMillis(70)
    }

    void "an id-only block sets the last event id of the state without an event"() {
        given:
        EventStreamState state = new EventStreamState()
        EventStreamDecoder decoder = new EventStreamDecoder(1024, state)

        when:
        List<Event<byte[]>> events = decoder.decode("id: 7\n\n".getBytes(StandardCharsets.UTF_8))

        then:
        events.isEmpty()
        state.lastEventId == "7"
        state.reconnectionTime == null

        when:
        events = decoder.decode("id: 8\n".getBytes(StandardCharsets.UTF_8))

        then: "the id is only taken at the end of the block"
        events.isEmpty()
        state.lastEventId == "7"

        when:
        events = decoder.decode("\ndata: a\n\n".getBytes(StandardCharsets.UTF_8))

        then:
        state.lastEventId == "8"
        describe(events) == [["a", "8", null, null]]
    }

    void "normal events are the same with or without a state"() {
        given:
        String stream = "id: 1\nevent: e\nretry: 10\ndata: a\n\nretry: 20\n\ndata: b\n\nid: 2\n\ndata: c\n\n"
        EventStreamState state = new EventStreamState()
        EventStreamDecoder withState = new EventStreamDecoder(1024, state)

        expect:
        describe(withState.decode(stream.getBytes(StandardCharsets.UTF_8))) == describe(decode(stream))
        describe(decode(stream)) == [["a", "1", "e", Duration.ofMillis(10)], ["b", "1", null, null], ["c", "2", null, null]]
        state.lastEventId == "2"
        state.reconnectionTime == Duration.ofMillis(20)
    }

    void "the decoder hands out the ids in delivery order, as markers for the blocks without data"() {
        given:
        EventStreamState state = new EventStreamState()
        EventStreamDecoder decoder = new EventStreamDecoder(1024, state)
        List<Event<byte[]>> events = []
        byte[] piece = "id: 1\ndata: a\n\nid: 2\ndata: b\n\nid: 3\n\nretry: 5\n\n".getBytes(StandardCharsets.UTF_8)

        when:
        decoder.decode(piece, 0, piece.length, events::add)

        then: "nothing is delivered yet, so the id is not taken, while the retry is"
        state.lastEventId == null
        state.reconnectionTime == Duration.ofMillis(5)
        events.collect { EventStreamDecoder.isControl(it) } == [false, false, true]

        when:
        EventStreamDecoder.deliver(events[0], state)

        then:
        state.lastEventId == "1"

        when:
        events.drop(1).each { EventStreamDecoder.deliver(it, state) }

        then:
        state.lastEventId == "3"
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
