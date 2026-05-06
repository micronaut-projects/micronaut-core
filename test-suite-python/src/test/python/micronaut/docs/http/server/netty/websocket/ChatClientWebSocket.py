from abc import ABC, abstractmethod

import java

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http import HttpRequest
from micronaut.websocket import WebSocketSession
from micronaut.websocket.annotation import ClientWebSocket, OnMessage, OnOpen

ConcurrentLinkedQueue = java.type("java.util.concurrent.ConcurrentLinkedQueue")
Future = java.type("java.util.concurrent.Future")
Publisher = java.type("org.reactivestreams.Publisher")
# end::imports[]


# tag::class[]
# TODO: Re-enable when Python @ClientWebSocket introduction can proxy Python classes.
# @ClientWebSocket("/chat/{topic}/{username}")  # <1>
class ChatClientWebSocket(ABC):  # <2>

    def __init__(self):
        self.session = None
        self.request = None
        self.topic = None
        self.username = None
        self.replies = ConcurrentLinkedQueue()

    @OnOpen
    def onOpen(
        self, topic: str, username: str, session: WebSocketSession, request: HttpRequest
    ) -> None:  # <3>
        self.topic = topic
        self.username = username
        self.session = session
        self.request = request

    def getTopic(self) -> str:
        return self.topic

    def getUsername(self) -> str:
        return self.username

    def getReplies(self):
        return self.replies

    def getSession(self) -> WebSocketSession:
        return self.session

    def getRequest(self) -> HttpRequest:
        return self.request

    @OnMessage
    def onMessage(self, message: str) -> None:
        self.replies.add(message)  # <4>

# end::class[]
    @abstractmethod
    def send(self, message: str) -> None:
        pass

    @abstractmethod
    def sendAsync(self, message: str) -> Future:
        pass

    @SingleResult
    @abstractmethod
    def sendRx(self, message: str) -> Publisher:
        pass
