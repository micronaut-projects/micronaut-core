import java

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpHeaderTuple, HttpRequest
from micronaut.http.server.filter import ResponseHeaderPopulator
from jakarta.inject import Singleton
# end::imports[]


@Requires(property="spec.filter", value="SecurityHeaderPopulator")
# tag::clazz[]
@Singleton  # <1>
class SecurityHeaderPopulator(ResponseHeaderPopulator):
    def findHttpHeader(self, request: HttpRequest) -> HttpHeaderTuple:  # <2>
        return HttpHeaderTuple("X-Content-Type-Options", "nosniff")  # <3>
# end::clazz[]
