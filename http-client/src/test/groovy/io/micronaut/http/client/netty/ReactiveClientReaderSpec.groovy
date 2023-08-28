package io.micronaut.http.client.netty

import io.micronaut.http.client.exceptions.ResponseClosedException
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.flow.FlowControlHandler
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.lang.Specification

class ReactiveClientReaderSpec extends Specification {
    def 'avoid nested onNext'() {
        def reader = new ReactiveClientReader() {
            @Override
            protected void remove(ChannelHandlerContext ctx) {
            }
        }
        def channel = new EmbeddedChannel()
        channel.config().setAutoRead(false)
        channel.pipeline().addLast(new FlowControlHandler(), reader)
        boolean nested = false
        Subscription sub = null
        HttpContent item = null
        reader.subscribe(new Subscriber<HttpContent>() {
            boolean inNext

            @Override
            void onSubscribe(Subscription s) {
                sub = s
            }

            @Override
            void onNext(HttpContent httpContent) {
                if (inNext) {
                    nested = true
                    throw new IllegalStateException()
                }
                inNext = true
                item = httpContent
                sub.request(1)
                inNext = false
            }

            @Override
            void onError(Throwable t) {
                if (inNext) {
                    nested = true
                    throw new IllegalStateException()
                }
            }

            @Override
            void onComplete() {
                if (inNext) {
                    nested = true
                    throw new IllegalStateException()
                }
            }
        })

        when:
        def c1 = new DefaultHttpContent(Unpooled.EMPTY_BUFFER)
        channel.writeInbound(c1)
        then:
        item == null

        when:
        sub.request(1)
        then:
        item == c1

        when:
        def c2 = new DefaultHttpContent(Unpooled.EMPTY_BUFFER)
        def c3 = new DefaultHttpContent(Unpooled.EMPTY_BUFFER)
        channel.writeInbound(c2, c3)
        then:
        item == c3
        !nested
    }

    def 'error before subscribe'() {
        given:
        def reader = new ReactiveClientReader() {
            @Override
            protected void remove(ChannelHandlerContext ctx) {
            }
        }
        def channel = new EmbeddedChannel(reader)
        def err = new RuntimeException()

        when:
        channel.pipeline().fireExceptionCaught(err)
        channel.checkException()
        then:
        noExceptionThrown()

        when:
        Flux.from(reader).blockLast()
        then:
        def e = thrown RuntimeException
        e == err
    }

    def 'inactive before subscribe'() {
        given:
        def reader = new ReactiveClientReader() {
            @Override
            protected void remove(ChannelHandlerContext ctx) {
            }
        }
        def channel = new EmbeddedChannel(reader)

        when:
        channel.pipeline().fireChannelInactive()
        channel.checkException()
        then:
        noExceptionThrown()

        when:
        Flux.from(reader).blockLast()
        then:
        thrown ResponseClosedException
    }
}
