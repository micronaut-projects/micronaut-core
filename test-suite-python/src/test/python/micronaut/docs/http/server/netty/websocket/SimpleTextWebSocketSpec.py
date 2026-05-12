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
        self.awaitReply(fred.getReplies(), "[bob] Joined!", 1)

        fred.send("Hello bob!")
        self.awaitReply(bob.getReplies(), "[fred] Hello bob!", 1)

        bob.send("Hi fred. How are things?")
        self.awaitReply(fred.getReplies(), "[bob] Hi fred. How are things?", 2)
        assert bob.getReplies().contains("[fred] Hello bob!")
        assert bob.getReplies().size() == 1

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

    def awaitReply(self, replies, expected: str, size: int) -> None:
        for _ in range(30):
            if replies.contains(expected) and replies.size() == size:
                return
            TimeUnit.MILLISECONDS.sleep(100)
        assert replies.contains(expected)
        assert replies.size() == size
