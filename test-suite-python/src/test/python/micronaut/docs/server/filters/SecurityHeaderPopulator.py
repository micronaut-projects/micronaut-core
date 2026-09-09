import java

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpHeaderEntry, HttpRequest, HttpResponse
from micronaut.http.server.filter import ResponseHeaderPopulator
from jakarta.inject import Singleton
# end::imports[]


@Requires(property="spec.filter", value="SecurityHeaderPopulator")
# tag::clazz[]
@Singleton  # <1>
class SecurityHeaderPopulator(ResponseHeaderPopulator):
    def findHttpHeaders(self, request: HttpRequest, response: HttpResponse) -> list[HttpHeaderEntry]:  # <2>
        return [HttpHeaderEntry("X-Content-Type-Options", "nosniff")]  # <3>
# end::clazz[]
