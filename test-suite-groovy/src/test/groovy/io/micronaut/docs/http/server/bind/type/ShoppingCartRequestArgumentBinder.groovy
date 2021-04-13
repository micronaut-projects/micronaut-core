package io.micronaut.docs.http.server.bind.type

// tag::class[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.http.cookie.Cookie
import io.micronaut.jackson.serialize.JacksonObjectSerializer

import javax.inject.Singleton

@Singleton
class ShoppingCartRequestArgumentBinder
        implements TypedRequestArgumentBinder<ShoppingCart> {

    private final JacksonObjectSerializer objectSerializer

    ShoppingCartRequestArgumentBinder(JacksonObjectSerializer objectSerializer) {
        this.objectSerializer = objectSerializer
    }

    @Override
    BindingResult<ShoppingCart> bind(ArgumentConversionContext<ShoppingCart> context,
                                     HttpRequest<?> source) { //<1>

        Cookie cookie = source.cookies.get("shoppingCart")
        if (!cookie) {
            return BindingResult.EMPTY
        }

        return () -> objectSerializer.deserialize( //<2>
                cookie.value.bytes,
                ShoppingCart)
    }

    @Override
    Argument<ShoppingCart> argumentType() {
        Argument.of(ShoppingCart) //<3>
    }
}
// end::class[]
