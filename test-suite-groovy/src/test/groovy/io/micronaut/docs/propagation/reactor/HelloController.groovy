package io.micronaut.docs.propagation.reactor

// tag::imports[]
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import reactor.core.publisher.Mono
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = 'spec.name', value = 'PropagatedContextSpec')
// tag::example[]
@Controller
class HelloController {

    @Get('/hello')
    Mono<String> hello(@QueryValue('name') String name) {
        PropagatedContext propagatedContext = PropagatedContext.get() + new MyContextElement(name) // <1>
        return Mono.just("Hello, $name")
                .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext)) // <2>
    }
}

record MyContextElement(String value) implements PropagatedContextElement { }
// end::example[]
