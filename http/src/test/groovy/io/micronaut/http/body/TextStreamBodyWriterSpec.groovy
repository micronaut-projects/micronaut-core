package io.micronaut.http.body

import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.codec.CodecException
import io.micronaut.http.simple.SimpleHttpHeaders
import io.micronaut.http.sse.Event
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

class TextStreamBodyWriterSpec extends Specification {

    def writer = new TextStreamBodyWriter<Event<String>>(MessageBodyHandlerRegistry.EMPTY)

    private String write(Event<String> event) {
        def out = new ByteArrayOutputStream()
        writer.writeTo(Argument.of(Event, String), MediaType.TEXT_EVENT_STREAM_TYPE, event, new SimpleHttpHeaders(), out)
        return out.toString(StandardCharsets.UTF_8)
    }

    void "the data is written as one data field per line"() {
        expect:
        write(Event.of(data)) == expected

        where:
        data          | expected
        'foo'         | 'data: foo\n\n'
        ''            | 'data:\n\n'
        'a\n'         | 'data: a\ndata:\n\n'
        '\n'          | 'data:\ndata:\n\n'
        'a\nb'        | 'data: a\ndata: b\n\n'
        'a\rb'        | 'data: a\ndata: b\n\n'
        'a\r\nb'      | 'data: a\ndata: b\n\n'
        'a\r\n'       | 'data: a\ndata:\n\n'
        'a\n\nb'      | 'data: a\ndata:\ndata: b\n\n'
    }

    void "every line of a comment is a comment"() {
        expect:
        write(Event.of('x').comment(comment)) == expected + 'data: x\n\n'

        where:
        comment          | expected
        'c'              | ': c\n'
        'a\nb'           | ': a\n: b\n'
        'a\r\ndata: b'   | ': a\n: data: b\n'
        'a\r\n'          | ': a\n:\n'
    }

    void "the fields of a normal event are kept"() {
        expect:
        write(Event.of('x').id('1').name('n').retry(Duration.ofSeconds(1))) == 'id: 1\nevent: n\nretry: 1000\ndata: x\n\n'
    }

    void "a line break in the id or name is rejected"() {
        when:
        write(event)

        then:
        thrown(CodecException)

        where:
        event << [
                Event.of('x').id('1\ndata: y'),
                Event.of('x').id('1\r'),
                Event.of('x').name('n\n\ndata: y'),
                Event.of('x').name('n\r\n'),
        ]
    }
}
