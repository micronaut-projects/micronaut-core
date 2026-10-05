from typing import Annotated

import builtins
import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

AsyncioConcurrentClientRunner = java.type("micronaut.docs.asyncio.AsyncioConcurrentClientRunner")


# Python singletons reached from async routes with the context pool enabled: a singleton is one object in one
# context, so its state is shared by every event loop, and it is never rebuilt without its Java dependencies.
@Property(name="spec.name", value="PythonAsyncSingletonSpec")
@Property(name="micronaut.netty.event-loops.default.num-threads", value="2")
@Property(name="micronaut.python.pool.enabled", value="true")
@MicronautTest
class PythonAsyncSingletonSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    def post(self, uri: str) -> str:
        return self.client.toBlocking().retrieve(HttpRequest.POST(uri, ""))

    @Test
    def asyncRoutesShareTheStateOfOneSingleton(self):
        before = int(self.client.toBlocking().retrieve("/async-singleton/count").split(":")[0])

        first = self.post("/async-singleton/increment")
        second = self.post("/async-singleton-route/increment")
        third = self.post("/async-singleton/increment")

        assert f"{before + 1}:True" == first, first
        assert f"{before + 2}:True" == second, second
        assert f"{before + 3}:True" == third, third
        count, requests = self.client.toBlocking().retrieve("/async-singleton/count").split(":")
        assert before + 3 == int(count), count
        # the async route updated the controller the sync route reads: there is one controller
        assert int(requests) >= 2, requests

    @Test
    def concurrentAsyncRequestsCountOnOneSingleton(self):
        before = int(self.client.toBlocking().retrieve("/async-singleton/count").split(":")[0])

        AsyncioConcurrentClientRunner.postConcurrently(self.client, "/async-singleton/increment", 16)

        count = int(self.client.toBlocking().retrieve("/async-singleton/count").split(":")[0])
        assert before + 16 == count, f"before={before}, count={count}"

    @Test
    def aSingletonControllerRunsInItsOwnContextAndARouteModuleInTheEventLoopContext(self):
        controller_context = self.client.toBlocking().retrieve("/async-singleton/context-id")
        route_context = self.client.toBlocking().retrieve("/async-singleton-route/context-id")

        assert controller_context == builtins.__MN_CTX_ID__, controller_context
        assert route_context != builtins.__MN_CTX_ID__, route_context
