from abc import ABC, abstractmethod

import java
from micronaut.websocket.annotation import ClientWebSocket, OnMessage

AutoCloseable = java.type("java.lang.AutoCloseable")
LinkedBlockingQueue = java.type("java.util.concurrent.LinkedBlockingQueue")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@ClientWebSocket("/")
class WebSocketRoutesClient(ABC, AutoCloseable):
    """The text messages of a connection to a WebSocket route."""

    def __init__(self):
        self.replies = LinkedBlockingQueue()

    @OnMessage
    def onMessage(self, message: str) -> None:
        self.replies.add(message)

    def next(self) -> str:
        message = self.replies.poll(5, TimeUnit.SECONDS)
        assert message is not None, "no message"
        return message

    @abstractmethod
    def send(self, message: str) -> None:
        ...
