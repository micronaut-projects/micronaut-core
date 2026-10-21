import time
from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Reports import Reports

Socket = java.type("java.net.Socket")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@Property(name="spec.name", value="ReportRoutesTest")
@MicronautTest
class ReportRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    server: Annotated[EmbeddedServer, Inject]
    reports: Annotated[Reports, Inject]

    @Test
    def the_route_sees_the_outcome_and_the_abandonment(self):
        self.reports.generate("1").complete("report 1")
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/reports/1")) == "report 1"
        assert not self.reports.outcome("1").get(10, TimeUnit.SECONDS).isPresent()

        report = self.reports.generate("2")
        socket = Socket(self.server.getHost(), self.server.getPort())
        try:
            out = socket.getOutputStream()
            out.write(bytes("GET /reports/2 HTTP/1.1\r\nHost: localhost\r\n\r\n", "ascii"))
            out.flush()
            deadline = time.time() + 10
            while report.getNumberOfDependents() == 0 and time.time() < deadline:
                time.sleep(0.02)
        finally:
            socket.close()
        # the client went away: the work is cancelled, and the outcome is the closed connection
        assert self.reports.outcome("2").get(10, TimeUnit.SECONDS).isPresent()
        assert report.isCancelled()
