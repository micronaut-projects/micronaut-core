package io.micronaut.docs.http.server.bind.type
// tag::class[]
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.bind.ArgumentBinder.BindingResult
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.jackson.serialize.JacksonObjectSerializer
import javax.inject.Singleton
import java.util.Optional

@Singleton
class ShoppingCartRequestArgumentBinder(private val objectSerializer: JacksonObjectSerializer) :
    TypedRequestArgumentBinder<ShoppingCart> {

    override fun bind(
            context: ArgumentConversionContext<ShoppingCart>,
            source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<ShoppingCart> { //<1>
        val cookie = source.cookies["shoppingCart"]
        return if (cookie != null) {
            BindingResult {
                objectSerializer.deserialize( // <2>
                    cookie.value.toByteArray(),
                    ShoppingCart::class.java
                )
            }
        } else BindingResult {
            Optional.empty()
        }
    }

    override fun argumentType(): Argument<ShoppingCart> {
        return Argument.of(ShoppingCart::class.java) //<3>
    }
}
// end::class[]
