from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.context import BeanContext
from micronaut.context.annotation import Executable
from micronaut.core.annotation import AnnotationValue
from micronaut.core.version.annotation import Version
from micronaut.http import HttpMethod, HttpResponse, MediaType, MutableHttpResponse
from micronaut.http.annotation import FilterMatcher, ResponseFilter, ServerFilter
from micronaut.scheduling import TaskExecutors
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

Integer = java.type("java.lang.Integer")
Thread = java.type("java.lang.Thread")
# end::imports[]


# tag::annotations[]
@FilterMatcher  # <1>
def Audited(func):
    return func


@Audited
@ServerFilter("/**")
@Requires(property="spec.name", value="AuditedRoutesTest")
class AuditFilter:  # <2>
    @ResponseFilter
    def audit(self, response: MutableHttpResponse) -> None:
        response.header("X-Audited", "true")


@Singleton
@Requires(property="spec.name", value="AuditedRoutesTest")
class Payments:
    @Executable
    @Audited
    def pay(self, amount: int) -> str:  # <3>
        return f"paid {amount}"
# end::annotations[]


@Requires(property="spec.name", value="AuditedRoutesTest")
@Singleton
class AuditedRoutes(HttpRoutes):
    def __init__(self, bean_context: BeanContext, payments: Payments):
        self.bean_context = bean_context
        self.payments = payments

    def routes(self, routes: HttpRouteBuilder) -> None:
        # tag::annotationRoutes[]
        def admin_routes(admin):
            (admin.executeOn(TaskExecutors.BLOCKING)  # <4>
                .annotate(Audited)  # <5>
                .annotate(Version, lambda version: version.value("2")))
            admin.GET("/users", lambda request, path_variables:
                      HttpResponse.ok("users on " + Thread.currentThread().getName()).contentType(MediaType.TEXT_PLAIN_TYPE))
            admin.DELETE("/users/{id}", lambda request, path_variables:
                         HttpResponse.ok(f"deleted {path_variables.getLong('id')}").contentType(MediaType.TEXT_PLAIN_TYPE))

        routes.path("/admin", admin_routes)
        (routes.POST("/payments/{amount}")
            .annotationMetadata(self.bean_context.getBeanDefinition(Payments).getRequiredMethod("pay", Integer.TYPE))  # <6>
            .handle(lambda request, path_variables:
                    HttpResponse.ok(self.payments.pay(path_variables.getLong("amount"))).contentType(MediaType.TEXT_PLAIN_TYPE)))
        (routes.POST("/refunds/{amount}")
            .annotate(Audited)  # <7>
            .handle(lambda request, path_variables:
                    HttpResponse.ok(f"refunded {path_variables.getLong('amount')}").contentType(MediaType.TEXT_PLAIN_TYPE)))
        (routes.GET("/receipts/{id}")
            .annotate(AnnotationValue.builder(Version).value("1").build())  # <8>
            .handle(lambda request, path_variables:
                   HttpResponse.ok(f"receipt v1 {path_variables.getLong('id')}").contentType(MediaType.TEXT_PLAIN_TYPE)))
        (routes.GET("/receipts/{id}")
            .annotate(Version, lambda version: version.value("2"))  # <9>
            .annotate(Audited)
            .handle(lambda request, path_variables:
                    HttpResponse.ok(f"receipt v2 {path_variables.getLong('id')}").contentType(MediaType.TEXT_PLAIN_TYPE)))
        routes.GET("/prices", lambda request, path_variables: HttpResponse.ok("prices").contentType(MediaType.TEXT_PLAIN_TYPE))
        # end::annotationRoutes[]
        routes.route(HttpMethod.GET, "/balance/{account}").handle(lambda request, path_variables:
                                                                  HttpResponse.ok("balance of " + path_variables.getString("account")).contentType(MediaType.TEXT_PLAIN_TYPE))
