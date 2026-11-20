from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, HttpResponse, HttpStatus, PathVariables
from micronaut.http.gateway import GatewayRequestContext
from micronaut.web.router.builder import RequestHandler


@Requires(property="spec.name", value="GatewayDocsTest")
# tag::clazz[]
@Singleton
@Named("ordersFallback")
class OrdersFallback(RequestHandler):

    def handle(self, request: HttpRequest, path_variables: PathVariables) -> HttpResponse:
        context = GatewayRequestContext.find(request)
        failure = context.get().failure() if context.isPresent() else None
        reason = failure.get().getClass().getSimpleName() if failure is not None and failure.isPresent() else "none"
        return HttpResponse.status(HttpStatus.OK).body("fallback: " + reason)
# end::clazz[]
