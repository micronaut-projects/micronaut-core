from abc import ABC, abstractmethod

import java

from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
from micronaut.http.sse import Event

from micronaut.docs.streaming.Headline import Headline

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/streaming/sse")
class HeadlineClient(ABC):

    @Get(value="/headlines", processes=MediaType.TEXT_EVENT_STREAM)
    @abstractmethod
    def streamHeadlines(self) -> Publisher[Event[Headline]]:
        ...
# end::class[]
