from typing import Annotated

import builtins
import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

AsyncioConcurrentClientRunner = java.type("micronaut.docs.asyncio.AsyncioConcurrentClientRunner")


@Property(name="spec.name", value="PythonAsyncioSpec")
@Property(name="micronaut.netty.event-loops.default.num-threads", value="1")
@Property(name="micronaut.netty.event-loops.client.num-threads", value="1")
@Property(name="micronaut.http.client.event-loop-group", value="client")
@Property(name="micronaut.http.client.pool.max-pending-connections", value="64")
@Property(name="micronaut.python.pool.enabled", value="true")
@MicronautTest
class PythonAsyncioSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def asyncBackendControllerCanSleep(self):
        assert "backend" == self.client.toBlocking().retrieve("/async-backend/message")

    @Test
    def asyncControllerCanAwaitAsyncClient(self):
        response = self.client.toBlocking().retrieve("/async-demo/message")
        assert "demo:backend" == response, response

    @Test
    def asyncControllerCanAwaitPublisherClient(self):
        response = self.client.toBlocking().retrieve("/async-demo/publisher-message")
        assert "demo:publisher-backend" == response, response

    @Test
    def asyncControllerCanAwaitDefaultHttpClientExchange(self):
        response = self.client.toBlocking().retrieve("/async-demo/http-client-exchange")
        assert "exchange:backend" == response, response

    @Test
    def asyncControllerCanUseTaskGroup(self):
        response = self.client.toBlocking().retrieve("/async-demo/task-group")
        assert "backend:sleep" == response, response

    @Test
    def taskGroupCancelsSiblingTasksOnFailure(self):
        response = self.client.toBlocking().retrieve("/async-demo/task-group-cancel")
        assert "failed=True:cleanup=True" == response, response

    @Test
    def classlessAsyncRouteCanAwaitClient(self):
        response = self.client.toBlocking().retrieve("/async-route-message")
        assert "route:backend" == response, response

    @Test
    def classlessAsyncRouteCanAwaitPublisherClient(self):
        response = self.client.toBlocking().retrieve("/async-route-publisher-message")
        assert "route:publisher-backend" == response, response

    @Test
    def asyncRequestsAreConcurrentOnSingleEventLoop(self):
        self.client.toBlocking().retrieve("/async-backend/reset-stats")
        elapsed_millis = AsyncioConcurrentClientRunner.retrieveConcurrently(
            self.client,
            "/async-demo/concurrent-message",
            "demo:backend",
            8,
        )
        max_active = int(self.client.toBlocking().retrieve("/async-backend/max-active"))

        assert max_active > 1, f"async client awaits did not overlap: max_active={max_active}, elapsed={elapsed_millis}ms"
        assert elapsed_millis < 1500, f"async requests took too long: elapsed={elapsed_millis}ms"

    @Test
    def asyncClientAwaitDoesNotBlockEventLoop(self):
        probe = self.client.toBlocking().retrieve("/async-demo/probe")
        before_thread, after_thread, heartbeat_elapsed, response = probe.split("|")

        assert "backend" == response, f"unexpected probe response: {probe}"
        assert before_thread == after_thread, f"event-loop thread changed: {probe}"
        assert int(heartbeat_elapsed) < 90, f"event-loop heartbeat was delayed: elapsed={heartbeat_elapsed}ms"

    @Test
    def asyncControllerUsesEventLoopContext(self):
        context_id = self.client.toBlocking().retrieve("/async-demo/context-id")

        assert context_id != builtins.__MN_CTX_ID__
