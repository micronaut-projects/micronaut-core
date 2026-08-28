from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property, Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MdcService import MdcService


@Requires(property="mdc.example.service.enabled")
@Controller("/mdc")
class MDCController:

    def __init__(self, mdcService: MdcService):
        self.mdcService = mdcService

    @Get(value="/test", produces=MediaType.TEXT_PLAIN)
    def test(self) -> str:
        return self.mdcService.createUser("Denis")


@Property(name="mdc.example.service.enabled", value=True)
@MicronautTest
class MdcServiceSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testFilterSpec(self) -> None:
        response = self.client.toBlocking().retrieve("/mdc/test")
        assert response.startswith("New user id: ")
        assert response.endswith(" name: Denis")
