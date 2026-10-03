from abc import ABC, abstractmethod

import java

from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

from .Tick import Tick

Publisher = java.type("org.reactivestreams.Publisher")


@Requires(property="spec.name", value="PythonStreamingSpec")
# tag::client[]
@Client("/async-streams")
class TickClient(ABC):

    @Get(value="/counted", processes=MediaType.APPLICATION_JSON_STREAM)
    @abstractmethod
    def counted(self) -> Publisher[Tick]:  # <1>
        ...
# end::client[]
