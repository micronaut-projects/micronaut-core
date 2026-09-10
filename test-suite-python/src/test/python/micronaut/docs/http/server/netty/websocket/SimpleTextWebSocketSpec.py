from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.websocket import WebSocketClient
from org.junit.jupiter.api import Test

Flux = java.type("reactor.core.publisher.Flux")
TimeUnit = java.type("java.util.concurrent.TimeUnit")
ChatClientClass = java.type("micronaut.docs.http.server.netty.websocket.ChatClientWebSocket")


@Property(name="spec.name", value="SimpleTextWebSocketSpec")
@MicronautTest
class SimpleTextWebSocketSpec:
    wsClient: Annotated[WebSocketClient, Inject, Client("/")]

    @Test
    def test_simple_text_websocket_exchange(self):
        fred = Flux.from_(
            self.wsClient.connect(ChatClientClass, "/chat/stuff/fred")
        ).blockFirst()
        bob = Flux.from_(
            self.wsClient.connect(ChatClientClass, {"topic": "stuff", "username": "bob"})
        ).blockFirst()

        assert fred.getSession() is not None
        assert fred.getSession().getId() is not None
        assert fred.getRequest() is not None
        assert fred.getSession().getId() != bob.getSession().getId()
        assert fred.getTopic() == "stuff"
        assert fred.getUsername() == "fred"
        assert bob.getUsername() == "bob"
        self.awaitReply(fred.getReplies(), "[bob] Joined!")

        fred.send("Hello bob!")
        self.awaitReply(bob.getReplies(), "[fred] Hello bob!")
        assert not fred.getReplies().contains("[fred] Hello bob!")
        assert not bob.getReplies().contains("[bob] Joined!")

        bob.send("Hi fred. How are things?")
        self.awaitReply(fred.getReplies(), "[bob] Hi fred. How are things?")
        assert not bob.getReplies().contains("[bob] Hi fred. How are things?")
        assert bob.getReplies().contains("[fred] Hello bob!")

        assert fred.sendAsync("foo").get() == "foo"
        assert Flux.from_(fred.sendRx("bar")).blockFirst() == "bar"

        bob.close()
        fred.close()
        for _ in range(30):
            if not bob.getSession().isOpen() and not fred.getSession().isOpen():
                break
            TimeUnit.MILLISECONDS.sleep(100)
        assert not bob.getSession().isOpen()
        assert not fred.getSession().isOpen()

    def awaitReply(self, replies, expected: str) -> None:
        # The reply count is deliberately not asserted: a broadcast only skips the session that
        # sent it, so whether a client sees an earlier client's "Joined!" depends on whether it
        # connected before that broadcast was delivered.
        for _ in range(30):
            if replies.contains(expected):
                return
            TimeUnit.MILLISECONDS.sleep(100)
        assert replies.contains(expected)
