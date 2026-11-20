from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest
from micronaut.http.gateway import KeyResolver


@Requires(property="spec.name", value="GatewayDocsTest")
# tag::clazz[]
@Singleton
@Named("tenant")
class TenantKeyResolver(KeyResolver):

    def resolve(self, request: HttpRequest) -> str | None:
        # synchronous: read the request, no IO
        return request.getHeaders().get("X-Tenant")
# end::clazz[]
