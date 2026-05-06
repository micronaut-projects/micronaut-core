from abc import ABC, abstractmethod

import java

# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/streaming")
class HeadlineClient(ABC):

    @Get(value="/headlines", processes=MediaType.APPLICATION_JSON_STREAM)  # <1>
    @abstractmethod
    def streamHeadlines(self) -> Publisher:  # <2>
        pass
# end::class[]

    @Get(value="/headlines", processes=MediaType.APPLICATION_JSON_STREAM)  # <1>
    @abstractmethod
    def streamFlux(self) -> Publisher:
        pass

# tag::endclass[]
# end::endclass[]
