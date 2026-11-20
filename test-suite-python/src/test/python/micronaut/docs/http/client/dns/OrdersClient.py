from micronaut.context.annotation import Requires
# tag::imports[]
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
# end::imports[]


@Requires(property="spec.name", value="DnsDiscoveryTest")
# tag::class[]
@Client(id="orders")  # <1>
class OrdersClient:

    @Get("/orders/{id}")
    def find(self, id: str) -> str:
        ...
# end::class[]
