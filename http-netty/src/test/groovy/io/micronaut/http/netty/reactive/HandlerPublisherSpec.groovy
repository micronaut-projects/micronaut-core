package io.micronaut.http.netty.reactive

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

class HandlerPublisherSpec extends Specification {
    def 'nested read'() {
        given:
        /*
         * This race condition appeared during micronaut-serialization development. It is a subtle violation of the
         * reactive streams spec: HandlerPublisher will sometimes call onComplete while onNext is still running. The
         * conditions for this are as follows:
         *
         * - HandlerPublisher receives a message and calls onNext while on the event loop.
         * - onNext triggers a channel.read(). Normally RoutingInBoundHandler does this, in a `doOnNext` task.
         * - The channel.read() runs through the pipeline immediately, because it's on the event loop.
         * - Normally the channel.read() would just cause an update to the nio selection key and not affect the current
         *   loop, but sometimes the netty FlowControlHandler still has messages buffered. It forwards one of those.
         * - That message is received by HttpStreamsHandler. If it's the last chunk of a request, HttpStreamsHandler
         *   removes the HandlerPublisher from the pipeline.
         * - HandlerPublisher.complete is called. Because there are no buffered messages, it calls onComplete
         *   immediately, even though we're still in the onNext invocation from the first point.
         */

        def embeddedChannel = new EmbeddedChannel()
        def handlerPublisher = new HandlerPublisher(embeddedChannel.eventLoop()) {
            @Override
            protected boolean acceptInboundMessage(Object msg) {
                return true
            }
        }
        boolean killOnNextRead = false
        embeddedChannel.pipeline().addLast(new ChannelDuplexHandler() {
            @Override
            void read(ChannelHandlerContext ctx) throws Exception {
                if (killOnNextRead) {
                    ctx.pipeline().remove("handler-publisher")
                }
            }
        })
        boolean messageReceived = false
        embeddedChannel.pipeline().addLast("handler-publisher", handlerPublisher)
        handlerPublisher.subscribe(new Subscriber() {
            Subscription s
            boolean inFlight = true

            @Override
            void onSubscribe(Subscription s) {
                this.s = s
                s.request(1)
            }

            @Override
            void onNext(Object o) {
                inFlight = true
                killOnNextRead = true
                embeddedChannel.read()
                messageReceived = true
                inFlight = false
            }

            @Override
            void onError(Throwable t) {
                t.printStackTrace()
            }

            @Override
            void onComplete() {
                if (inFlight) {
                    throw new IllegalStateException("concurrent call to onComplete!")
                }
            }
        })
        embeddedChannel.runPendingTasks()

        when:
        embeddedChannel.pipeline().fireChannelRead('foo')
        embeddedChannel.runPendingTasks()
        then:
        noExceptionThrown()
        embeddedChannel.checkException()
        messageReceived
    }
}
