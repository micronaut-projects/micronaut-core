from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Requires
from micronaut.http.annotation import Get

from .BackendClient import BackendClient

backend_client: Annotated[BackendClient, Inject]


@Requires(property="spec.name", value="PythonAsyncioSpec")
# tag::classless[]
@Get("/async-route-message")
async def async_route_message() -> str:
    return "route:" + await backend_client.message()
# end::classless[]


@Requires(property="spec.name", value="PythonAsyncioSpec")
# tag::classlessPublisher[]
@Get("/async-route-publisher-message")
async def async_route_publisher_message() -> str:
    return "route:" + await backend_client.publisher_message()
# end::classlessPublisher[]
