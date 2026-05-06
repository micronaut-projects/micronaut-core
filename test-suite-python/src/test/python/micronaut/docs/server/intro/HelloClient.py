# tag::imports[]
from abc import ABC, abstractmethod

import java
from micronaut.core.async_.annotation import SingleResult
from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

Publisher = java.type("org.reactivestreams.Publisher")
# end::imports[]


# tag::class[]
@Client("/hello")  # <1>
class HelloClient(ABC):

    @Get(consumes=MediaType.TEXT_PLAIN)  # <2>
    @SingleResult
    @abstractmethod
    def hello(self) -> Publisher:  # <3>
        pass
# end::class[]
