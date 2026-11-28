from abc import abstractmethod

# tag::imports[]
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
# end::imports[]


# tag::class[]
@Client(id="inventory")  # <1>
class InventoryClient:

    @Get("/inventory/{item}")  # <2>
    @abstractmethod
    def stock(self, item: str) -> str:
        ...
# end::class[]
