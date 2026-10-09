package io.micronaut.http.client.netty

import io.micronaut.http.body.stream.BodySizeLimits
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class SseSplitterSpec extends Specification {
    def sync(String input, List<String> expectedOutput) {
        given:
        ByteBuf inp = Unpooled.copiedBuffer(input, StandardCharsets.UTF_8)

        when:
        def parts = SseSplitter.split(inp).collect { it.toString(StandardCharsets.UTF_8) }
        then:
        parts == expectedOutput

        where:
        input        | expectedOutput
        'foo'        | ['foo']
        'foo\r\n'    | ['foo', '']
        'foo\r\nbar' | ['foo', 'bar']
        'foo\n'      | ['foo', '']
        'foo\nbar'   | ['foo', 'bar']
    }

    def async(List<String> input, List<String> expectedOutput) {
        when:
        def parts = SseSplitter
                .split(Flux.fromIterable(input).map { Unpooled.copiedBuffer(it, StandardCharsets.UTF_8) }, BodySizeLimits.UNLIMITED)
                .map { it.toString(StandardCharsets.UTF_8) }
                .collectList().block()

        then:
        parts == expectedOutput

        where:
        input                | expectedOutput
        ['foo\n']            | ['foo']
        ['foo\r\n\r\n']      | ['foo', '']
        ['foo\r\nbar\r\n']   | ['foo', 'bar']
        ['foo\n\n']          | ['foo', '']
        ['foo\nbar\n']       | ['foo', 'bar']
        ['fo', 'o\n']        | ['foo']
        ['fo', 'o\r\n\n']    | ['foo', '']
        ['fo', 'o\r\nbar\n'] | ['foo', 'bar']
        ['foo\r', '\nbar\n'] | ['foo', 'bar']
        ['foo\r\nb', 'ar\n'] | ['foo', 'bar']
        ['fo', 'o\n\n']      | ['foo', '']
        ['fo', 'o\nbar\n']   | ['foo', 'bar']
    }

    def 'partial line is released on cancel'() {
        given:
        ByteBuf input = Unpooled.copiedBuffer('foo\nba', StandardCharsets.UTF_8)

        when:
        def first = SseSplitter
                .split(Flux.just(input).concatWith(Flux.never()), BodySizeLimits.UNLIMITED)
                .next()
                .block()

        then:
        first.toString(StandardCharsets.UTF_8) == 'foo'
        input.refCnt() == 0

        cleanup:
        first?.release()
    }

    def 'partial line is released when the line limit is exceeded'() {
        given:
        // retain once more so that a double release is visible in the reference count
        ByteBuf a = Unpooled.copiedBuffer('fo', StandardCharsets.UTF_8).retain()
        ByteBuf b = Unpooled.copiedBuffer('obarbaz\n', StandardCharsets.UTF_8).retain()

        when:
        SseSplitter
                .split(Flux.just(a, b), new BodySizeLimits(Long.MAX_VALUE, 5))
                .collectList()
                .block()

        then:
        thrown(io.micronaut.http.client.exceptions.ContentLengthExceededException)
        a.refCnt() == 1
        b.refCnt() == 1

        cleanup:
        a.release()
        b.release()
    }
}
