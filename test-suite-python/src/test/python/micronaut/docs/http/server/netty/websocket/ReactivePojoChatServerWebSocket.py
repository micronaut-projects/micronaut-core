import java
from typing import Annotated

from micronaut.http.annotation import Body
from micronaut.context.annotation import Requires
from micronaut.websocket import WebSocketBroadcaster, WebSocketSession
from micronaut.websocket.annotation import OnClose, OnMessage, OnOpen, ServerWebSocket

from .Message import Message

Publisher = java.type("org.reactivestreams.Publisher")
String = java.type("java.lang.String")


@Requires(property="spec.name", value="PojoWebSocketSpec")
@ServerWebSocket("/pojo/chat/{topic}/{username}")
class ReactivePojoChatServerWebSocket:

    def __init__(self, broadcaster: WebSocketBroadcaster):
        self.broadcaster = broadcaster

    @OnOpen
    def onOpen(
        self, topic: str, username: str, session: WebSocketSession
    ) -> Publisher:
        text = "[" + username + "] Joined!"
        message = Message(text)
        return self.broadcaster.broadcast(message, self.isValid(topic, session))

    # tag::onmessage[]
    @OnMessage
    def onMessage(
        self, topic: str, username: str, message: Annotated[Message, Body], session: WebSocketSession
    ) -> Publisher:
        text = "[" + username + "] " + message.getText()
        newMessage = Message(text)
        return self.broadcaster.broadcast(newMessage, self.isValid(topic, session))
    # end::onmessage[]

    @OnClose
    def onClose(
        self, topic: str, username: str, session: WebSocketSession
    ) -> Publisher:
        text = "[" + username + "] Disconnected!"
        message = Message(text)
        return self.broadcaster.broadcast(message, self.isValid(topic, session))

    def isValid(self, topic: str, session: WebSocketSession):
        return lambda s: s != session and topic.lower() == str(
            s.getUriVariables().get("topic", String, None)
        ).lower()
