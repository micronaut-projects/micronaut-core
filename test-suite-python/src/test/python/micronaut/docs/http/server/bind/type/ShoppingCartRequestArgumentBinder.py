# tag::class[]
import java
from jakarta.inject import Singleton
from micronaut.core.bind import ArgumentBinder
from micronaut.core.convert import ArgumentConversionContext
from micronaut.core.type import Argument
from micronaut.http import HttpRequest
from micronaut.http.bind.binders import TypedRequestArgumentBinder
from micronaut.jackson.serialize import JacksonObjectSerializer

ShoppingCartClass = java.type("micronaut.docs.http.server.bind.type.ShoppingCart")


@Singleton
class ShoppingCartRequestArgumentBinder(TypedRequestArgumentBinder):
    def __init__(self, objectSerializer: JacksonObjectSerializer):
        self.objectSerializer = objectSerializer

    def bind(self, context: ArgumentConversionContext, source: HttpRequest):  # <1>
        cookie = source.getCookies().get("shoppingCart")
        if cookie is None:
            return ArgumentBinder.BindingResult.empty()

        return lambda: self.objectSerializer.deserialize(  # <2>
            bytearray(cookie.getValue(), "utf-8"),
            ShoppingCartClass,
        )

    def argumentType(self):
        return Argument.of(ShoppingCartClass)  # <3>
# end::class[]
