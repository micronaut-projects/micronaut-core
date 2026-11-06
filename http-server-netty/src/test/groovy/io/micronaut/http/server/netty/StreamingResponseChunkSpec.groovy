package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.LastHttpContent
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Tests for how a streaming response is split into outbound messages: an element and the separator
 * in front of it belong in the same message, but an element must never wait for the next one.
 */
class StreamingResponseChunkSpec extends Specification {

    private static EmbeddedChannel channel(ApplicationContext ctx, Monitor mon) {
        def server = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def channel = server.buildEmbeddedChannel(false)
        // outbound messages travel from PipeliningServerHandler towards the head of the pipeline,
        // so a handler placed before it sees the HttpContents before they are encoded
        channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND, "monitor", mon)
        return channel
    }

    private static void request(EmbeddedChannel channel, String uri, String accept) {
        channel.writeInbound(Unpooled.wrappedBuffer(
                ("GET " + uri + " HTTP/1.1\r\nhost: example.com\r\naccept: " + accept + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8)))
        channel.runPendingTasks()
    }

    def 'a streamed json array sends one content message per element'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StreamingResponseChunkSpec'])
        def mon = new Monitor()
        def channel = channel(ctx, mon)

        when:
        request(channel, '/streaming-chunks/json', 'application/json')

        then: 'the bytes on the wire are unchanged'
        mon.contents.join('') == '[1,2,3]'

        and: 'each element travels with the separator in front of it, and the array is closed by the terminating message'
        mon.contents == ['[1', ',2', ',3', ']']
        mon.lastContentIndex == 3

        and: 'writes inside a single read cycle share one flush'
        mon.flushes == 1

        cleanup:
        channel.finishAndReleaseAll()
        ctx.close()
    }

    def 'an asynchronously produced json array element is written and flushed as one message'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StreamingResponseChunkSpec'])
        def sink = ctx.getBean(JsonSink).sink
        def mon = new Monitor()
        def channel = channel(ctx, mon)

        when:
        request(channel, '/streaming-chunks/json-async', 'application/json')
        then: 'nothing is written before the first element'
        mon.contents.isEmpty()

        when:
        sink.tryEmitNext(1)
        channel.runPendingTasks()
        then: 'the opening bracket and the element are one message and one flush'
        mon.contents == ['[1']
        mon.flushes == 1

        when:
        sink.tryEmitNext(2)
        channel.runPendingTasks()
        then: 'the separator and the element are one message and one flush'
        mon.contents == ['[1', ',2']
        mon.flushes == 2

        when:
        sink.tryEmitComplete()
        channel.runPendingTasks()
        then: 'the closing bracket is the terminating message'
        mon.contents == ['[1', ',2', ']']
        mon.lastContentIndex == 2
        mon.flushes == 3

        cleanup:
        channel.finishAndReleaseAll()
        ctx.close()
    }

    def 'an empty streamed json array sends a single content message'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StreamingResponseChunkSpec'])
        def mon = new Monitor()
        def channel = channel(ctx, mon)

        when:
        request(channel, '/streaming-chunks/json-empty', 'application/json')

        then:
        mon.contents.join('') == '[]'
        mon.contents == ['[]']
        mon.lastContentIndex == 0

        cleanup:
        channel.finishAndReleaseAll()
        ctx.close()
    }

    def 'server sent events are not held back waiting for the next one'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StreamingResponseChunkSpec'])
        def sink = ctx.getBean(SseSink).sink
        def mon = new Monitor()
        def channel = channel(ctx, mon)

        when:
        request(channel, '/streaming-chunks/sse', MediaType.TEXT_EVENT_STREAM)
        then: 'nothing is written before the first event'
        mon.contents.isEmpty()

        when: 'a single event is produced'
        sink.tryEmitNext(Event.of('one'))
        channel.runPendingTasks()
        then: 'it is on the wire and flushed immediately, without waiting for a second event'
        mon.contents == ['data: one\n\n']
        mon.flushes == 1

        when:
        sink.tryEmitNext(Event.of('two'))
        channel.runPendingTasks()
        then:
        mon.contents == ['data: one\n\n', 'data: two\n\n']
        mon.flushes == 2

        when:
        sink.tryEmitComplete()
        channel.runPendingTasks()
        then:
        mon.contents == ['data: one\n\n', 'data: two\n\n', '']
        mon.lastContentIndex == 2

        cleanup:
        channel.finishAndReleaseAll()
        ctx.close()
    }

    static class Monitor extends ChannelOutboundHandlerAdapter {
        final List<String> contents = []
        int flushes = 0
        int lastContentIndex = -1

        @Override
        void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof HttpContent) {
                if (msg instanceof LastHttpContent) {
                    lastContentIndex = contents.size()
                }
                contents.add(((HttpContent) msg).content().toString(StandardCharsets.UTF_8))
            }
            super.write(ctx, msg, promise)
        }

        @Override
        void flush(ChannelHandlerContext ctx) throws Exception {
            flushes++
            super.flush(ctx)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'StreamingResponseChunkSpec')
    static class SseSink {
        final Sinks.Many<Event<String>> sink = Sinks.many().unicast().onBackpressureBuffer()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'StreamingResponseChunkSpec')
    static class JsonSink {
        final Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer()
    }

    @Singleton
    @Controller('/streaming-chunks')
    @Requires(property = 'spec.name', value = 'StreamingResponseChunkSpec')
    static class StreamController {
        final SseSink sseSink
        final JsonSink jsonSink

        StreamController(SseSink sseSink, JsonSink jsonSink) {
            this.sseSink = sseSink
            this.jsonSink = jsonSink
        }

        @Get('/json')
        @Produces(MediaType.APPLICATION_JSON)
        Flux<Integer> json() {
            return Flux.just(1, 2, 3)
        }

        @Get('/json-async')
        @Produces(MediaType.APPLICATION_JSON)
        Flux<Integer> jsonAsync() {
            return jsonSink.sink.asFlux()
        }

        @Get('/json-empty')
        @Produces(MediaType.APPLICATION_JSON)
        Flux<Integer> jsonEmpty() {
            return Flux.empty()
        }

        @Get('/sse')
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> sse() {
            return sseSink.sink.asFlux()
        }
    }
}
