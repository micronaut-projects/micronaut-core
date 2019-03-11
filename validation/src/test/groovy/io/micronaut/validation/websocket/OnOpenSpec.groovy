package io.micronaut.validation.websocket

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class OnOpenSpec extends AbstractTypeElementSpec {

    void "test allowed parameters"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

@ClientWebSocket("/chat/{topic}/{username}") // <1>
abstract class ChatClientWebSocket { // <2>

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session, HttpRequest request) { 
       
    }
}

""")

        then:
        noExceptionThrown()
    }
}
