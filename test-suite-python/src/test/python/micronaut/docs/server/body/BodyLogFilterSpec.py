from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Base64 = java.type("java.util.Base64")
ListAppender = java.type("ch.qos.logback.core.read.ListAppender")
LoggerFactory = java.type("org.slf4j.LoggerFactory")
Thread = java.type("java.lang.Thread")


@Property(name="spec.name", value="BodyLogFilterSpec")
@MicronautTest
class BodyLogFilterSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def simple(self):
        appender = ListAppender()
        logger = LoggerFactory.getLogger("micronaut.docs.server.body")
        logger.addAppender(appender)
        appender.start()

        try:
            body = '{"firstName": "Jonas", "lastName": "Konrad"}'
            status = self.client.toBlocking().retrieve(
                HttpRequest.POST("/person", body),
                HttpStatus,
            )

            for _ in range(20):
                if appender.list.size() >= 2:
                    break
                Thread.sleep(50)

            messages = {
                appender.list.get(0).getFormattedMessage(),
                appender.list.get(1).getFormattedMessage(),
            }
            expected = {
                "Received body: " + Base64.getEncoder().encodeToString(
                    body.encode("utf-8")
                ),
                "Creating person Person[firstName=Jonas, lastName=Konrad]",
            }

            assert status == HttpStatus.OK or status == HttpStatus.NO_CONTENT
            assert messages == expected
        finally:
            logger.detachAppender(appender)
