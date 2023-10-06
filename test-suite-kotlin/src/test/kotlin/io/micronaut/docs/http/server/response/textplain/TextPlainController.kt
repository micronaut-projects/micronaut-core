package io.micronaut.docs.http.server.response.textplain

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.*
import kotlin.Long
import kotlin.String

@Requires(property = "spec.name", value = "TextPlainControllerTest")
//tag::classopening[]
@Controller("/txt")
class TextPlainController {
//end::classopening[]
    @Get("/boolean")
    @Produces(MediaType.TEXT_PLAIN) // <1>
    fun bool(): String {
        return true.toString() // <2>
    }

    @Get("/boolean/mono")
    @Produces(MediaType.TEXT_PLAIN) // <1>
    @SingleResult
    fun monoBool(): Publisher<String> {
        return Mono.just(true.toString()) // <2>
    }

    @Get("/boolean/flux")
    @Produces(MediaType.TEXT_PLAIN) // <1>
    @SingleResult
    fun fluxBool(): Publisher<String> {
        return Flux.just(true.toString()) // <2>
    }

    @Get("/bigdecimal")
    @Produces(MediaType.TEXT_PLAIN) // <1>
    fun bigDecimal(): String {
        return BigDecimal.valueOf(Long.MAX_VALUE).toString() // <2>
    }

//tag::method[]
    @Get("/date")
    @Produces(MediaType.TEXT_PLAIN) // <1>
    fun date(): String {
        return Calendar.Builder().setDate(2023, 7, 4).build().toString() // <2>
    }

//end::method[]
    @Get("/person")
    @Produces(MediaType.TEXT_PLAIN) // <1>
    fun person(): String {
        return Person("Dean Wette", 65).toString() // <2>
    }
//tag::classclosing[]
}
//end::classclosing[]
