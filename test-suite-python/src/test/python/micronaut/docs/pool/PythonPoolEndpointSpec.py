from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Map = java.type("java.util.Map")


@Property(name="endpoints.pythonpool.sensitive", value="false")
@Property(name="micronaut.python.pool.enabled", value="true")
@Property(name="micronaut.python.pool.size", value="1")
@MicronautTest
class PythonPoolEndpointSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def poolStatisticsAreExposed(self):
        # tag::endpoint[]
        statistics = self.client.toBlocking().retrieve(HttpRequest.GET("/pythonpool"), Map)
        # end::endpoint[]

        assert statistics.get("enabled") == True
        assert statistics.get("targetSize") == 1
        assert statistics.get("pooledContexts") >= 0
        assert statistics.get("idleContexts") >= 0
        assert statistics.get("eventLoopContexts") >= 0
        assert statistics.get("borrows") >= 0
        assert statistics.get("waits") >= 0
        assert statistics.get("totalWaitMillis") >= 0
        assert statistics.get("maxWaitMillis") >= 0
        assert statistics.get("closed") == False
