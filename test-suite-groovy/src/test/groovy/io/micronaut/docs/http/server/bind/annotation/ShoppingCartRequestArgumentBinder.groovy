package io.micronaut.docs.http.server.bind.annotation

// tag::class[]
import groovy.transform.CompileStatic
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.micronaut.http.cookie.Cookie
import io.micronaut.jackson.serialize.JacksonObjectSerializer

import jakarta.inject.Singleton

@CompileStatic
@Singleton
class ShoppingCartRequestArgumentBinder
        implements AnnotatedRequestArgumentBinder<ShoppingCart, Object> { //<1>

    private final ConversionService<?> conversionService
    private final JacksonObjectSerializer objectSerializer

    ShoppingCartRequestArgumentBinder(
            ConversionService<?> conversionService,
            JacksonObjectSerializer objectSerializer) {
        this.conversionService = conversionService
        this.objectSerializer = objectSerializer
    }

    @Override
    Class<ShoppingCart> getAnnotationType() {
        ShoppingCart
    }

    @Override
    BindingResult<Object> bind(
            ArgumentConversionContext<Object> context,
            HttpRequest<?> source) { //<2>

        String parameterName = context.annotationMetadata
                .stringValue(ShoppingCart)
                .orElse(context.argument.name)

        Cookie cookie = source.cookies.get("shoppingCart")
        if (!cookie) {
            return BindingResult.EMPTY
        }

        Optional<Map<String, Object>> cookieValue = objectSerializer.deserialize(
                cookie.value.bytes,
                Argument.mapOf(String, Object))

        return (BindingResult) { ->
            cookieValue.flatMap({value ->
                conversionService.convert(value.get(parameterName), context)
            })
        }
    }
}
// end::class[]
