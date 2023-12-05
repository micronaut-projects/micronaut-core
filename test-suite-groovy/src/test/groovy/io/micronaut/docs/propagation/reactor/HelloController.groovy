package io.micronaut.docs.propagation.reactor

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.context.ServerHttpRequestContext
import reactor.core.publisher.Mono

@Requires(property = 'spec.name', value = 'PropagatedContextSpec')
// tag::example[]
@Controller
class HelloController {

    @Get('/hello')
    Mono<String> hello(HttpRequest<?> httpRequest) {
        PropagatedContext propagatedContext = PropagatedContext.get() + new ServerHttpRequestContext(httpRequest) // <1>
        return Mono.just('Hello, World')
                .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext)) // <2>
    }
}
// end::example[]
