package io.micronaut.docs.http.bind.binders
// tag::class[]
import groovy.transform.CompileStatic
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.micronaut.http.cookie.Cookie
import io.micronaut.jackson.serialize.JacksonObjectSerializer

import javax.inject.Singleton

@CompileStatic
@Singleton
class MyAnnotatedRequestArgumentBinder implements AnnotatedRequestArgumentBinder<MyBindingAnnotation, Object> { //<1>

    private final ConversionService<?> conversionService
    private final JacksonObjectSerializer objectSerializer

    MyAnnotatedRequestArgumentBinder(
            ConversionService<?> conversionService,
            JacksonObjectSerializer objectSerializer) {
        this.conversionService = conversionService
        this.objectSerializer = objectSerializer
    }

    @Override
    Class<MyBindingAnnotation> getAnnotationType() {
        return MyBindingAnnotation.class
    }

    @Override
    BindingResult<Object> bind(
            ArgumentConversionContext<Object> context,
            HttpRequest<?> source) { //<2>

        String parameterName = context.annotationMetadata
                .stringValue(MyBindingAnnotation)
                .orElse(context.argument.name)

        Cookie cookie = source.cookies.get("shoppingCart")

        if (cookie != null) {
            Optional<Map<String, Object>> cookieValue

            cookieValue = objectSerializer.deserialize(
                    cookie.value.bytes,
                    Argument.mapOf(String, Object))

            if(cookieValue.isPresent()) {
                Optional<Object> value = conversionService.convert(cookieValue.get().get(parameterName), context)

                return new BindingResult<Object>() {
                    @Override
                    Optional<Object> getValue() {
                        return value
                    }
                }
            }
        }

        return BindingResult.EMPTY
    }
}
// end::class[]
