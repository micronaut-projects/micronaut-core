from abc import ABC, abstractmethod
from typing import Annotated

import java

from .Metadata import Metadata

from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client

Map = java.type("java.util.Map")


# tag::clazz[]
@Client("/")
class MetadataClient(ABC):

    @Get("/client/bind")
    @abstractmethod
    def get(self, metadata: Annotated[Map, Metadata]) -> str:
        ...
# end::clazz[]
