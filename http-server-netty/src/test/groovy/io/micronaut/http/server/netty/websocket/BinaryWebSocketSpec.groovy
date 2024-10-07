/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.client.netty.NettyClientCustomizer
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPipeline
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class BinaryWebSocketSpec extends Specification {

    void "test binary websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('spec.name': 'BinaryWebSocketSpec', 'micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = Flux.from(wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred")).blockFirst()
        BinaryChatClientWebSocket bob = Flux.from(wsClient.connect(BinaryChatClientWebSocket, [topic:"stuff",username:"bob"])).blockFirst()

        then:"The connection is valid"
        fred.session != null
        fred.session.id != null

        then:"A session is established"
        fred.session != null
        fred.session.id != null
        fred.session.id != bob.session.id
        fred.topic == 'stuff'
        fred.username == 'fred'
        bob.username == 'bob'
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        when:"A message is sent"
        fred.send("Hello bob!".bytes)

        then:
        conditions.eventually {
            bob.replies.contains("[fred] Hello bob!")
            bob.replies.size() == 1
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        when:
        bob.send("Hi fred. How are things?".bytes)

        then:
        conditions.eventually {
            fred.replies.contains("[bob] Hi fred. How are things?")
            fred.replies.size() == 2
            bob.replies.contains("[fred] Hello bob!")
            bob.replies.size() == 1
        }
        def buffer = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        buffer.retain()
        fred.sendAsync(buffer).get().toString(StandardCharsets.UTF_8) == 'foo'
        new String(Mono.from(fred.sendRx(ByteBuffer.wrap("bar".bytes))).block().array()) == 'bar'

        when:
        bob.close()

        then:
        conditions.eventually {
            !bob.session.isOpen()
        }

        when:
        fred.send("Damn bob left".bytes)

        then:
        conditions.eventually {
            fred.replies.contains("[bob] Disconnected!")
            !bob.replies.contains("[bob] Disconnected!")
        }

        cleanup:
        wsClient.close()
        embeddedServer.close()
    }

    void "test sending multiple frames for a single message"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('spec.name': 'BinaryWebSocketSpec', 'micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockFirst()
        BinaryChatClientWebSocket bob = wsClient.connect(BinaryChatClientWebSocket, [topic:"stuff",username:"bob"]).blockFirst()


        then:"The connection is valid"
        fred.session != null
        fred.session.id != null
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        when:"A message is sent"
        fred.sendMultiple()

        then:
        conditions.eventually {
            bob.replies.contains("[fred] hello world")
        }

        cleanup:
        fred.close()
        bob.close()
        wsClient.close()
        embeddedServer.close()
    }

    void "test sending many continuation frames"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('spec.name': 'BinaryWebSocketSpec', 'micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockFirst()
        BinaryChatClientWebSocket bob = wsClient.connect(BinaryChatClientWebSocket, [topic:"stuff",username:"bob"]).blockFirst()


        then:"The connection is valid"
        fred.session != null
        fred.session.id != null
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        when:"A message is sent"
        fred.sendMany()

        then:
        conditions.eventually {
            bob.replies.contains("[fred] abcdef")
        }

        cleanup:
        fred.close()
        bob.close()
        wsClient.close()
        embeddedServer.close()
    }

    void "test sending ping messages"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('spec.name': 'BinaryWebSocketSpec', 'micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when:"a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockFirst()

        then:"The connection is valid"
        fred.session != null
        fred.session.id != null

        when:'A ping is sent'
        fred.sendPing('foo')

        then:
        conditions.eventually {
            fred.pingReplies.contains('foo') && fred.pingReplies.size() == 1
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6069')
    void "test per-message compression"() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'BinaryWebSocketSpec',
                'method.name'            : 'test per-message compression',
                'micronaut.server.port': -1
        ])
        def cdcServer = ctx.getBean(CompressionDetectionCustomizerServer)
        def cdcClient = ctx.getBean(CompressionDetectionCustomizerClient)
        EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockFirst()
        BinaryChatClientWebSocket bob = wsClient.connect(BinaryChatClientWebSocket, [topic: "stuff", username: "bob"]).blockFirst()


        then: "The connection is valid"
        fred.session != null
        fred.session.id != null
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        cdcServer.getPipelines().size() == 2
        cdcClient.getPipelines().size() == 2

        when: "A message is sent"
        List<MessageInterceptor> interceptors = new ArrayList<>()
        for (ChannelPipeline pipeline : cdcServer.getPipelines() + cdcClient.getPipelines()) {
            def interceptor = new MessageInterceptor()
            if (pipeline.get('ws-encoder') != null) {
                pipeline.addAfter('ws-encoder', 'MessageInterceptor', interceptor)
            } else {
                pipeline.addAfter('wsencoder', 'MessageInterceptor', interceptor)
            }
            interceptors.add(interceptor)
        }

        fred.sendMany()

        then: "Messages were compressed"
        conditions.eventually {
            bob.replies.contains("[fred] abcdef")
        }
        interceptors.any { it.seenCompressedMessage }

        cleanup:
        fred.close()
        bob.close()
        wsClient.close()
        embeddedServer.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/7711')
    void "test per-message compression disabled"() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'BinaryWebSocketSpec',
                'method.name'            : 'test per-message compression',
                'micronaut.server.port': -1,
                'micronaut.http.client.ws.compression.enabled': false
        ])
        def cdcServer = ctx.getBean(CompressionDetectionCustomizerServer)
        def cdcClient = ctx.getBean(CompressionDetectionCustomizerClient)
        EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockFirst()
        BinaryChatClientWebSocket bob = wsClient.connect(BinaryChatClientWebSocket, [topic: "stuff", username: "bob"]).blockFirst()


        then: "The connection is valid"
        fred.session != null
        fred.session.id != null
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        cdcServer.getPipelines().size() == 2
        cdcClient.getPipelines().size() == 2

        when: "A message is sent"
        List<MessageInterceptor> interceptors = new ArrayList<>()
        for (ChannelPipeline pipeline : cdcServer.getPipelines() + cdcClient.getPipelines()) {
            def interceptor = new MessageInterceptor()
            if (pipeline.get('ws-encoder') != null) {
                pipeline.addAfter('ws-encoder', 'MessageInterceptor', interceptor)
            } else {
                pipeline.addAfter('wsencoder', 'MessageInterceptor', interceptor)
            }
            interceptors.add(interceptor)
        }

        fred.sendMany()

        then: "Messages were not compressed"
        conditions.eventually {
            bob.replies.contains("[fred] abcdef")
        }
        interceptors.every { !it.seenCompressedMessage }

        cleanup:
        fred.close()
        bob.close()
        wsClient.close()
        embeddedServer.close()
    }

    @Singleton
    @Requires(property = 'method.name', value = 'test per-message compression')
    static class CompressionDetectionCustomizerServer implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        List<ChannelPipeline> pipelines = Collections.synchronizedList(new ArrayList<>())

        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.getBean().register(new Customizer(null))
            return event.bean
        }

        class Customizer implements NettyServerCustomizer {
            final Channel channel

            Customizer(Channel channel) {
                this.channel = channel
            }

            @Override
            NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                return new Customizer(channel)
            }

            @Override
            void onInitialPipelineBuilt() {
                pipelines.add(channel.pipeline())
            }
        }
    }

    @Singleton
    @Requires(property = 'method.name', value = 'test per-message compression')
    static class CompressionDetectionCustomizerClient implements BeanCreatedEventListener<NettyClientCustomizer.Registry> {
        List<ChannelPipeline> pipelines = Collections.synchronizedList(new ArrayList<>())

        @Override
        NettyClientCustomizer.Registry onCreated(BeanCreatedEvent<NettyClientCustomizer.Registry> event) {
            event.getBean().register(new Customizer(null))
            return event.bean
        }

        class Customizer implements NettyClientCustomizer {
            final Channel channel

            Customizer(Channel channel) {
                this.channel = channel
            }

            @Override
            NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                return new Customizer(channel)
            }

            @Override
            void onInitialPipelineBuilt() {
                pipelines.add(channel.pipeline())
            }
        }
    }

    static class MessageInterceptor extends ChannelOutboundHandlerAdapter {
        boolean seenCompressedMessage = false

        @Override
        void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof WebSocketFrame) {
                seenCompressedMessage |= (msg.rsv() & 0x4) != 0
            }
            super.write(ctx, msg, promise)
        }
    }
}
