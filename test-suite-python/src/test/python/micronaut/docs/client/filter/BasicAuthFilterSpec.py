from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property, Requires
from micronaut.http.annotation import Controller, Get
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .BasicAuthClient import BasicAuthClient

HttpBasicAuth = java.type("io.micronaut.http.BasicAuth")


@Requires(property="spec.name", value="BasicAuthFilterSpec")
@Controller("/message")
class BasicAuthController:

    @Get
    def message(self, basicAuth: HttpBasicAuth) -> str:
        return basicAuth.getUsername() + ":" + basicAuth.getPassword()


@Property(name="spec.name", value="BasicAuthFilterSpec")
@MicronautTest
class BasicAuthFilterSpec:
    client: Annotated[BasicAuthClient, Inject]

    @Test
    @Disabled("Python custom annotation stereotypes are not validated yet for @FilterMatcher client matching")
    def testTheFilterIsApplied(self) -> None:
        assert self.client.getMessage() == "user:pass"
