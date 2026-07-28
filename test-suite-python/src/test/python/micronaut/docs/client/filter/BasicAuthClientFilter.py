# tag::class[]
from jakarta.inject import Singleton
from micronaut.http import MutableHttpRequest
from micronaut.http.annotation import ClientFilter, RequestFilter

from .BasicAuth import BasicAuth


@BasicAuth  # <1>
@Singleton  # <2>
@ClientFilter
class BasicAuthClientFilter:

    @RequestFilter
    def filter(self, request: MutableHttpRequest) -> None:
        request.basicAuth("user", "pass")
# end::class[]
