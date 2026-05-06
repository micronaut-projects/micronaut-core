from abc import ABC, abstractmethod

from .NameAuthorization import NameAuthorization

from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client


# tag::clazz[]
@Client("/")
class NameAuthorizedClient(ABC):

    @Get("/client/authorized-resource")
    @NameAuthorization(name="Bob")  # <1>
    @abstractmethod
    def get(self) -> str:
        pass
# end::clazz[]
