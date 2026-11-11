from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.http import HttpResponse, HttpStatus, MediaType
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
IllegalArgumentException = java.type("java.lang.IllegalArgumentException")
IllegalStateException = java.type("java.lang.IllegalStateException")
Map = java.type("java.util.Map")
RuntimeException = java.type("java.lang.RuntimeException")
# end::imports[]


# tag::clazz[]
class NoSuchOrder(RuntimeException):
    """Thrown when an order is not known."""

    def __init__(self, id: int):
        super().__init__(f"No order {id}")


# end::clazz[]
@Requires(property="spec.name", value="ErrorRoutesTest")
# tag::clazz[]
@Singleton
class ErrorRoutes(HttpRoutes):

    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.error(NoSuchOrder, lambda request, error:  # <1>
                     HttpResponse.notFound(Map.of("error", error.getMessage())))
        routes.status(HttpStatus.NOT_FOUND, lambda request:  # <2>
                      HttpResponse.notFound(Map.of("error", "Nothing at " + request.getPath())))

        def order(request, path_variables):
            raise NoSuchOrder(path_variables.getLong("id"))

        routes.GET("/orders/{id}", order)

        def checkout_routes(checkout):
            checkout.error(IllegalArgumentException, lambda request, error:  # <3>
                           HttpResponse.badRequest(Map.of("invalid", error.getMessage())))
            checkout.errorAsync(IllegalStateException, lambda request, error:  # <4>
                                CompletableFuture.supplyAsync(lambda:
                                    HttpResponse.status(HttpStatus.CONFLICT).body(Map.of("conflict", error.getMessage()))))

            def place(request, path_variables):
                quantity = path_variables.getInt("quantity")
                if quantity <= 0:
                    raise IllegalArgumentException("quantity must be positive")
                if quantity > 10:
                    raise IllegalStateException("not enough stock")
                return HttpResponse.ok(f"ordered {quantity}").contentType(MediaType.TEXT_PLAIN_TYPE)

            checkout.POST("/{quantity}", place)

        routes.path("/checkout", checkout_routes)

        def pricing(request, path_variables):
            if path_variables.getInt("quantity") <= 0:
                raise IllegalArgumentException("quantity must be positive")
            return HttpResponse.ok("price").contentType(MediaType.TEXT_PLAIN_TYPE)

        routes.GET("/pricing/{quantity}", pricing)  # <5>
# end::clazz[]
