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

@Requires(property = "spec.name", value = "PropagatedContextSpec")
// tag::example[]
@Controller
class HelloController {

    @Get("/hello")
    fun hello(@QueryValue("name") name: String): Mono<String> {
        val propagatedContext = PropagatedContext.get().plus(MyContextElement(name)) // <1>
        return Mono.just("Hello, $name")
            .contextWrite { ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext) } // <2>
    }
}

data class MyContextElement(val value: String) : PropagatedContextElement
// end::example[]
