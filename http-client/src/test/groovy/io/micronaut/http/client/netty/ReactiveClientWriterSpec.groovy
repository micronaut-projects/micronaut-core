package io.micronaut.http.client.netty

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ReactiveClientWriterSpec extends Specification {
    def 'last content as part of publisher'() {
        given:
        Sinks.Many<HttpContent> sink = Sinks.many().unicast().onBackpressureBuffer()
        def writer = new ReactiveClientWriter(sink.asFlux())
        def channel = new EmbeddedChannel(writer)

        when:
        def c1 = new DefaultHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8)))
        sink.tryEmitNext(c1)
        then:
        channel.readOutbound() == c1

        when:
        def c2 = new DefaultLastHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8)))
        sink.tryEmitNext(c2)
        then:
        channel.readOutbound() == c2

        when:
        sink.tryEmitComplete()
        then:
        channel.readOutbound() == null
    }

    def 'last content from onComplete'() {
        given:
        Sinks.Many<HttpContent> sink = Sinks.many().unicast().onBackpressureBuffer()
        def writer = new ReactiveClientWriter(sink.asFlux())
        def channel = new EmbeddedChannel(writer)

        when:
        def c1 = new DefaultHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8)))
        sink.tryEmitNext(c1)
        then:
        channel.readOutbound() == c1

        when:
        sink.tryEmitComplete()
        then:
        channel.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT

        when:
        sink.tryEmitComplete()
        then:
        channel.readOutbound() == null
    }
}
