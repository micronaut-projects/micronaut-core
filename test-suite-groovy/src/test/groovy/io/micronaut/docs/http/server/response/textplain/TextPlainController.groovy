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

@Requires(property = 'spec.name', value = 'TextPlainControllerTest')
// tag::class[]
@Controller('/txt')
class TextPlainController {

    @Get('/boolean')
    @Produces(MediaType.TEXT_PLAIN) // <1>
    String bool() {
        return Boolean.TRUE.toString() // <2>
    }

    @Get('/boolean/mono')
    @Produces(MediaType.TEXT_PLAIN) // <1>
    @SingleResult
    Publisher<String> monoBool() {
        return Mono.just(Boolean.TRUE.toString()) // <2>
    }

    @Get('/boolean/flux')
    @Produces(MediaType.TEXT_PLAIN)
    @SingleResult
    Publisher<String> fluxBool() {
        return Flux.just(Boolean.TRUE.toString())
    }

    @Get('/bigdecimal')
    @Produces(MediaType.TEXT_PLAIN) // <1>
    String bigDecimal() {
        return BigDecimal.valueOf(Long.MAX_VALUE).toString() // <2>
    }

    @Get('/date')
    @Produces(MediaType.TEXT_PLAIN) // <1>
    String date() {
        return new Calendar.Builder().setDate(2023,7,4).build().toString() // <2>
    }

    @Get('/person')
    @Produces(MediaType.TEXT_PLAIN) // <1>
    String person() {
        return new Person('Dean Wette', 65).toString() // <2>
    }
}
// end::class[]
