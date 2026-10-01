/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException
import java.nio.channels.ClosedChannelException

class AlpnHandshakeFailureSpec extends Specification {

    private static ApplicationContext newContext() {
        ApplicationContext.run([
                'micronaut.server.http-version'         : '2.0',
                'micronaut.ssl.enabled'                 : true,
                'micronaut.server.ssl.port'             : -1,
                'micronaut.server.ssl.build-self-signed': true,
        ])
    }

    void 'a failed handshake event is forwarded downstream exactly once'() {
        given:
        ApplicationContext ctx = newContext()
        def server = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def channel = server.buildEmbeddedChannel(true)
        def received = []
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            void userEventTriggered(ChannelHandlerContext context, Object evt) {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    received.add(evt)
                }
                context.fireUserEventTriggered(evt)
            }
        })
        def alpn = channel.pipeline().get(ApplicationProtocolNegotiationHandler)
        def event = new SslHandshakeCompletionEvent(new SSLHandshakeException('handshake failed'))

        when:
        alpn.userEventTriggered(channel.pipeline().context(alpn), event)

        then:
        received == [event]

        cleanup:
        channel.close()
        ctx.close()
    }

    void 'a handshake aborted by a closed channel is not forwarded downstream'() {
        given:
        ApplicationContext ctx = newContext()
        def server = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def channel = server.buildEmbeddedChannel(true)
        def received = []
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            void userEventTriggered(ChannelHandlerContext context, Object evt) {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    received.add(evt)
                }
                context.fireUserEventTriggered(evt)
            }
        })
        def alpn = channel.pipeline().get(ApplicationProtocolNegotiationHandler)
        def event = new SslHandshakeCompletionEvent(new ClosedChannelException())

        when:
        alpn.userEventTriggered(channel.pipeline().context(alpn), event)

        then:
        received.isEmpty()

        cleanup:
        channel.close()
        ctx.close()
    }
}
