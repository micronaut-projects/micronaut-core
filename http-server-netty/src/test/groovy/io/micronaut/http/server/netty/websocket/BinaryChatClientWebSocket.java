/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.netty.buffer.ByteBuf;
import io.reactivex.Single;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

@ClientWebSocket("/binary/chat/{topic}/{username}")
public abstract class BinaryChatClientWebSocket implements AutoCloseable{

    private WebSocketSession session;
    private String topic;
    private String username;
    private Collection<String> replies = new ConcurrentLinkedQueue<>();

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) {
        this.topic = topic;
        this.username = username;
        this.session = session;
        System.out.println("Client session opened for username = " + username);
    }

    public String getTopic() {
        return topic;
    }

    public String getUsername() {
        return username;
    }

    public Collection<String> getReplies() {
        return replies;
    }

    public WebSocketSession getSession() {
        return session;
    }

    @OnMessage
    public void onMessage(
            byte[] message) {
        System.out.println("Client received message = " + new String(message));
        replies.add(new String(message));
    }

    public abstract void send(byte[] message);

    public abstract Future<ByteBuf> sendAsync(ByteBuf message);

    public abstract Single<ByteBuffer> sendRx(ByteBuffer message);
}
