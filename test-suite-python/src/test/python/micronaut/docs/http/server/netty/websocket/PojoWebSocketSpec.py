from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.websocket import WebSocketClient
from org.junit.jupiter.api import Disabled, Test

from .Message import Message

Flux = java.type("reactor.core.publisher.Flux")
TimeUnit = java.type("java.util.concurrent.TimeUnit")
PojoChatClientClass = java.type("micronaut.docs.http.server.netty.websocket.PojoChatClientWebSocket")


@Property(name="spec.name", value="PojoWebSocketSpec")
@MicronautTest
@Disabled("Python @ClientWebSocket introduction cannot proxy Python classes yet")
class PojoWebSocketSpec:
    wsClient: Annotated[WebSocketClient, Inject, Client("/")]

    @Test
    def test_pojo_websocket_exchange(self):
        fred = getattr(Flux, "from")(
            self.wsClient.connect(PojoChatClientClass, "/pojo/chat/stuff/fred")
        ).blockFirst()
        bob = getattr(Flux, "from")(
            self.wsClient.connect(PojoChatClientClass, {"topic": "stuff", "username": "bob"})
        ).blockFirst()

        assert fred.getTopic() == "stuff"
        assert fred.getUsername() == "fred"
        assert bob.getUsername() == "bob"
        self.awaitReply(fred.getReplies(), "[bob] Joined!", 1)

        fred.send(Message("Hello bob!"))
        self.awaitReply(bob.getReplies(), "[fred] Hello bob!", 1)

        bob.send(Message("Hi fred. How are things?"))
        self.awaitReply(fred.getReplies(), "[bob] Hi fred. How are things?", 2)
        assert self.containsText(bob.getReplies(), "[fred] Hello bob!")
        assert bob.getReplies().size() == 1

        assert fred.sendAsync(Message("foo")).get().getText() == "foo"
        assert getattr(Flux, "from")(fred.sendRx(Message("bar"))).blockFirst().getText() == "bar"

        bob.close()
        fred.close()

    def awaitReply(self, replies, expected: str, size: int) -> None:
        for _ in range(30):
            if self.containsText(replies, expected) and replies.size() == size:
                return
            TimeUnit.MILLISECONDS.sleep(100)
        assert self.containsText(replies, expected)
        assert replies.size() == size

    def containsText(self, replies, expected: str) -> bool:
        iterator = replies.iterator()
        while iterator.hasNext():
            if iterator.next().getText() == expected:
                return True
        return False
