package io.micronaut.validation

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.RequestBean
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Inject

import javax.validation.ConstraintViolationException
import javax.validation.Valid

// this has to be a top-level class because groovy
@Requires(property = 'spec.name', value = 'WebSocketClientValidationSpec')
@ServerWebSocket('/validated')
class WebSocketValidationServerHandler {
    @Inject
    WebSocketClientValidationSpec.HolderBean holderBean

    @OnOpen
    @Validated
    def onOpen(@RequestBean @Valid WebSocketClientValidationSpec.ValidatedData data, WebSocketSession session) {
        holderBean.seenData = data
    }

    @OnMessage
    void onMessage(byte[] message) {
    }

    @OnError
    def onError(ConstraintViolationException e) {
        holderBean.seenError = e
    }
}
