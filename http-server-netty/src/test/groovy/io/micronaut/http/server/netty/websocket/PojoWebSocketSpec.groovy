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
import io.micronaut.core.convert.DefaultConversionService
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.RxWebSocketClient
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.ChannelProgressivePromise
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.Attribute
import io.netty.util.AttributeKey
import io.netty.util.concurrent.EventExecutor
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class PojoWebSocketSpec extends Specification {


    void "test POJO websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        PojoChatClientWebSocket fred = wsClient.connect(PojoChatClientWebSocket, "/pojo/chat/stuff/fred").blockingFirst()
        PojoChatClientWebSocket bob = wsClient.connect(PojoChatClientWebSocket, [topic:"stuff",username:"bob"]).blockingFirst()

        then:"A session is established"
        fred.topic == 'stuff'
        fred.username == 'fred'
        bob.username == 'bob'

        conditions.eventually {
            fred.replies.contains(new Message(text:"[bob] Joined!"))
            fred.replies.size() == 1
        }

        when:"A message is sent"
        fred.send(new Message(text: "Hello bob!"))

        then:
        conditions.eventually {
            bob.replies.contains(new Message(text:"[fred] Hello bob!"))
            fred.replies.contains(new Message(text:"[bob] Joined!"))
            !fred.replies.contains(new Message(text:"[fred] Hello bob!"))
            !bob.replies.contains(new Message(text:"[bob] Joined!"))
        }

        when:
        bob.send(new Message(text: "Hi fred. How are things?"))

        then:
        conditions.eventually {
            fred.replies.contains(new Message(text:"[bob] Hi fred. How are things?"))
            !bob.replies.contains(new Message(text:"[bob] Hi fred. How are things?"))
            bob.replies.contains(new Message(text:"[fred] Hello bob!"))
        }
        fred.sendAsync(new Message(text:  "foo")).get().text == 'foo'
        fred.sendRx(new Message(text:  "bar")).blockingGet().text == 'bar'

        cleanup:
        bob?.close()
        fred?.close()

        wsClient.close()
        embeddedServer.close()
    }

    void "Netty WS upgrade handler correctly recognizes update to WS with more than one hop header allowed"() {
        given: "the relevant headers as sent by Firefox and as parsed by Netty"
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "http://localhost:8080/topics/person")
        req.headers().add(HttpHeaderNames.CONNECTION.toString(), "keep-alive, Upgrade")
        req.headers().add(HttpHeaderNames.UPGRADE.toString(), "websocket")

        when: "running with just enough infra mocked up to test the recognition of the upgrade"
        NettyServerWebSocketUpgradeHandler handler = new NettyServerWebSocketUpgradeHandler(null,
                null,
                null,
                null,
                null,
                null)
        NettyHttpRequest<Void> message = new NettyHttpRequest<>(req,
                new MyChannelHandlerContext(),
                new DefaultConversionService(),
                new HttpServerConfiguration())

        expect: "The handler should consider this an upgrade request"
        handler.acceptInboundMessage(message) == true
    }

    static class MyChannelHandlerContext implements ChannelHandlerContext {

        @Override
        public Channel channel() {
            return null;
        }

        @Override
        public EventExecutor executor() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public ChannelHandler handler() {
            return null;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            return null;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture disconnect() {
            return null;
        }

        @Override
        public ChannelFuture close() {
            return null;
        }

        @Override
        public ChannelFuture deregister() {
            return null;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelHandlerContext read() {
            return null;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return null;
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelHandlerContext flush() {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return null;
        }

        @Override
        public ChannelPromise newPromise() {
            return null;
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return null;
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return null;
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return null;
        }

        @Override
        public ChannelPromise voidPromise() {
            return null;
        }

        @Override
        public ChannelPipeline pipeline() {
            return null;
        }

        @Override
        public ByteBufAllocator alloc() {
            return null;
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return null;
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return false;
        }
    }
}