from abc import ABC, abstractmethod

import java
from micronaut.core.async_.annotation import SingleResult
from micronaut.websocket.annotation import ClientWebSocket, OnMessage, OnOpen

from .Message import Message

ConcurrentLinkedQueue = java.type("java.util.concurrent.ConcurrentLinkedQueue")
Future = java.type("java.util.concurrent.Future")
Publisher = java.type("org.reactivestreams.Publisher")


# TODO: Re-enable when Python @ClientWebSocket introduction can proxy Python classes.
# @ClientWebSocket("/pojo/chat/{topic}/{username}")
class PojoChatClientWebSocket(ABC):

    def __init__(self):
        self.topic = None
        self.username = None
        self.replies = ConcurrentLinkedQueue()

    @OnOpen
    def onOpen(self, topic: str, username: str) -> None:
        self.topic = topic
        self.username = username

    def getTopic(self) -> str:
        return self.topic

    def getUsername(self) -> str:
        return self.username

    def getReplies(self):
        return self.replies

    @OnMessage
    def onMessage(self, message: Message) -> None:
        self.replies.add(message)

    @abstractmethod
    def send(self, message: Message) -> None:
        pass

    @abstractmethod
    def sendAsync(self, message: Message) -> Future:
        pass

    @SingleResult
    @abstractmethod
    def sendRx(self, message: Message) -> Publisher:
        pass
