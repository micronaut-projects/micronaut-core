package io.micronaut.docs.propagation.reactor

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import reactor.core.publisher.Mono

@Requires(property = 'spec.name', value = 'PropagatedContextSpec')
// tag::example[]
@Controller
class HelloController {

    @Get('/hello')
    Mono<String> hello() {
        PropagatedContext propagatedContext = PropagatedContext.get() + new MyContextElement() // <1>
        return Mono.just('Hello, World')
                .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext)) // <2>
    }
}

class MyContextElement implements PropagatedContextElement { /*...*/ }
// end::example[]
