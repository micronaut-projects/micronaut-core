from abc import abstractmethod

# tag::class[]
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

from .BasicAuth import BasicAuth


@BasicAuth  # <1>
@Client("/message")
class BasicAuthClient:

    @Get
    @abstractmethod
    def getMessage(self) -> str:
        pass
# end::class[]
