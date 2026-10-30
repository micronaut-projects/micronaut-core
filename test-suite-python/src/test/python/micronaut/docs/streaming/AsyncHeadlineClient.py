from abc import ABC, abstractmethod

import java

# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.body import BodyElements
from micronaut.http.client.annotation import Client

from .Headline import Headline
# end::imports[]

CompletionStage = java.type("java.util.concurrent.CompletionStage")


# tag::class[]
@Client("/streaming")
class AsyncHeadlineClient(ABC):

    @Get(value="/headlines", processes=MediaType.APPLICATION_JSON_STREAM)
    @abstractmethod
    def streamHeadlines(self) -> CompletionStage[BodyElements[Headline]]:  # <1>
        ...
# end::class[]
