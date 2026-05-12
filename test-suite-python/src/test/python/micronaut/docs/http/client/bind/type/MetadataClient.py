from abc import ABC, abstractmethod

from .Metadata import Metadata

from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client


# tag::clazz[]
@Client("/")
class MetadataClient(ABC):

    @Get("/client/bind")
    @abstractmethod
    def get(self, metadata: Metadata) -> str:
        ...
# end::clazz[]
