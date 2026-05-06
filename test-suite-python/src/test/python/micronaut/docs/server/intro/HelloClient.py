# tag::imports[]
from abc import ABC, abstractmethod

import java
from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

Publisher = java.type("org.reactivestreams.Publisher")
# end::imports[]


# tag::class[]
@Client("/hello")  # <1>
class HelloClient(ABC):

    @Get(consumes=MediaType.TEXT_PLAIN)  # <2>
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    @abstractmethod
    def hello(self) -> Publisher:  # <3>
        pass
# end::class[]
