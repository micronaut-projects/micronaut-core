import java

#tag::clazz[]
from micronaut.context.annotation import Requires
from micronaut.websocket import WebSocketBroadcaster, WebSocketSession
from micronaut.websocket.annotation import OnClose, OnMessage, OnOpen, ServerWebSocket

String = java.type("java.lang.String")


@Requires(property="spec.name", value="SimpleTextWebSocketSpec")
@ServerWebSocket("/chat/{topic}/{username}")  # <1>
class ChatServerWebSocket:

    def __init__(self, broadcaster: WebSocketBroadcaster):
        self.broadcaster = broadcaster

    @OnOpen  # <2>
    def onOpen(self, topic: str, username: str, session: WebSocketSession) -> None:
        msg = "[" + username + "] Joined!"
        self.broadcaster.broadcastSync(msg, self.isValid(topic, session))

    @OnMessage  # <3>
    def onMessage(
        self, topic: str, username: str, message: str, session: WebSocketSession
    ) -> None:
        msg = "[" + username + "] " + message
        self.broadcaster.broadcastSync(msg, self.isValid(topic, session))  # <4>

    @OnClose  # <5>
    def onClose(self, topic: str, username: str, session: WebSocketSession) -> None:
        msg = "[" + username + "] Disconnected!"
        self.broadcaster.broadcastSync(msg, self.isValid(topic, session))

    def isValid(self, topic: str, session: WebSocketSession):
        return lambda s: s != session and topic.lower() == str(
            s.getUriVariables().get("topic", String, None)
        ).lower()
#end::clazz[]
