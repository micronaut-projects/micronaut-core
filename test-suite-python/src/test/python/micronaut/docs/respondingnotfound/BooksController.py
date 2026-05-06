import java
from micronaut.context.annotation import Requires
# tag::clazz[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Controller, Get

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
Map = java.type("java.util.Map")


@Requires(property="spec.name", value="respondingnotfound")
@Controller("/books")
class BooksController:

    @Get("/stock/{isbn}")
    def stock(self, isbn: str) -> Map:
        return None  # <1>

    @Get("/maybestock/{isbn}")
    @SingleResult
    def maybestock(self, isbn: str) -> Publisher:
        return Mono.empty()  # <2>
# end::clazz[]
