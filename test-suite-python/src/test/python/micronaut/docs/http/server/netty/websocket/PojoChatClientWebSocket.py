from abc import ABC, abstractmethod
from typing import Annotated

import java
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Body
from micronaut.websocket.annotation import ClientWebSocket, OnMessage, OnOpen

from .Message import Message

ConcurrentLinkedQueue = java.type("java.util.concurrent.ConcurrentLinkedQueue")
AutoCloseable = java.type("java.lang.AutoCloseable")
Collection = java.type("java.util.Collection")
Future = java.type("java.util.concurrent.Future")
Publisher = java.type("org.reactivestreams.Publisher")


@ClientWebSocket("/pojo/chat/{topic}/{username}")
class PojoChatClientWebSocket(ABC, AutoCloseable):

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

    def getReplies(self) -> Collection[Message]:
        return self.replies

    @OnMessage
    def onMessage(self, message: Annotated[Message, Body]) -> None:
        self.replies.add(message)

    @abstractmethod
    def send(self, message: Message) -> None:
        pass

    @abstractmethod
    def sendAsync(self, message: Message) -> Future[Message]:
        pass

    @SingleResult
    @abstractmethod
    def sendRx(self, message: Message) -> Publisher[Message]:
        pass
