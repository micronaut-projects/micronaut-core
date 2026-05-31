from abc import ABC, abstractmethod

import java

from micronaut.context.annotation import Requires
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

Publisher = java.type("org.reactivestreams.Publisher")


@Requires(property="spec.name", value="PythonAsyncioSpec")
@Client("/async-backend")
class BackendClient(ABC):

    # tag::asyncClient[]
    @Get("/message")
    @abstractmethod
    async def message(self) -> str:
        ...
    # end::asyncClient[]

    # tag::publisherClient[]
    @Get("/publisher-message")
    @abstractmethod
    def publisher_message(self) -> Publisher[str]:
        ...
    # end::publisherClient[]
