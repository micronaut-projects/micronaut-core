/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.validation.websocket

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class OnMessageSpec extends AbstractTypeElementSpec {

    void "test a message handler with one message argument"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

@ServerWebSocket("/chat/{topic}")
class ChatServerWebSocket {

    @OnMessage
    public void onMessage(String topic, String message, WebSocketSession session) {
    }
}
""")

        then:
        noExceptionThrown()
    }

    void "test a pong handler with the pong argument"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

@ServerWebSocket("/chat")
class ChatServerWebSocket {

    @OnMessage
    public void onMessage(String message) {
    }

    @OnMessage
    public void onPong(WebSocketPongMessage pong, WebSocketSession session) {
    }
}
""")

        then:
        noExceptionThrown()
    }

    void "test a message handler with two message arguments fails"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.websocket.annotation.*;

@ServerWebSocket("/chat")
class ChatServerWebSocket {

    @OnMessage
    public void onMessage(String message, String other) {
    }
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("WebSocket @OnMessage handler has multiple possible message body arguments")
    }

    void "test a pong handler with another message argument fails"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.annotation.*;

@ServerWebSocket("/chat")
class ChatServerWebSocket {

    @OnMessage
    public void onPong(WebSocketPongMessage pong, String message) {
    }
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("WebSocket @OnMessage handler has multiple possible message body arguments")
    }

    void "test a message handler without a message argument fails"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

@ServerWebSocket("/chat/{topic}")
class ChatServerWebSocket {

    @OnMessage
    public void onMessage(String topic, WebSocketSession session) {
    }
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("WebSocket @OnMessage handler must define a message body argument")
    }

    void "test a message handler without parameters fails"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.websocket.annotation.*;

@ClientWebSocket("/chat")
abstract class ChatClientWebSocket {

    @OnMessage
    public void onMessage() {
    }
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("WebSocket @OnMessage handler must define a message body argument")
    }
}
