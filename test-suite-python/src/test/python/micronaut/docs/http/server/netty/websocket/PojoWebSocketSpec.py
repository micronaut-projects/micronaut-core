from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.websocket import WebSocketClient
from org.junit.jupiter.api import Test

from .Message import Message

Flux = java.type("reactor.core.publisher.Flux")
TimeUnit = java.type("java.util.concurrent.TimeUnit")
PojoChatClientClass = java.type("micronaut.docs.http.server.netty.websocket.PojoChatClientWebSocket")


@Property(name="spec.name", value="PojoWebSocketSpec")
@MicronautTest
class PojoWebSocketSpec:
    wsClient: Annotated[WebSocketClient, Inject, Client("/")]

    @Test
    def test_pojo_websocket_exchange(self):
        fred = Flux.from_(
            self.wsClient.connect(PojoChatClientClass, "/pojo/chat/stuff/fred")
        ).blockFirst()
        bob = Flux.from_(
            self.wsClient.connect(PojoChatClientClass, {"topic": "stuff", "username": "bob"})
        ).blockFirst()

        assert fred.getTopic() == "stuff"
        assert fred.getUsername() == "fred"
        assert bob.getUsername() == "bob"
        self.awaitReply(fred.getReplies(), "[bob] Joined!")

        fred.send(Message("Hello bob!"))
        self.awaitReply(bob.getReplies(), "[fred] Hello bob!")
        assert not self.containsText(fred.getReplies(), "[fred] Hello bob!")
        assert not self.containsText(bob.getReplies(), "[bob] Joined!")

        bob.send(Message("Hi fred. How are things?"))
        self.awaitReply(fred.getReplies(), "[bob] Hi fred. How are things?")
        assert not self.containsText(bob.getReplies(), "[bob] Hi fred. How are things?")
        assert self.containsText(bob.getReplies(), "[fred] Hello bob!")

        assert fred.sendAsync(Message("foo")).get().getText() == "foo"
        assert Flux.from_(fred.sendRx(Message("bar"))).blockFirst().getText() == "bar"

        bob.close()
        fred.close()

    def awaitReply(self, replies, expected: str) -> None:
        # The reply count is deliberately not asserted: a broadcast only skips the session that
        # sent it, so whether a client sees an earlier client's "Joined!" depends on whether it
        # connected before that broadcast was delivered.
        for _ in range(150):
            if self.containsText(replies, expected):
                return
            TimeUnit.MILLISECONDS.sleep(100)
        assert self.containsText(replies, expected)

    def containsText(self, replies, expected: str) -> bool:
        iterator = replies.iterator()
        while iterator.hasNext():
            if iterator.next().getText() == expected:
                return True
        return False
