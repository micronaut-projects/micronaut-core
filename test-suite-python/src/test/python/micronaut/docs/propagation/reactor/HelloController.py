from dataclasses import dataclass
from typing import Annotated

import java
from micronaut.context.annotation import Requires

# tag::imports[]
# TODO: Re-enable when Python can import from io.micronaut.core.async.propagation.
# from micronaut.core.async.propagation import ReactorPropagation
from micronaut.http.annotation import Controller, Get, QueryValue
# end::imports[]

Mono = java.type("reactor.core.publisher.Mono")
PropagatedContext = java.type("io.micronaut.core.propagation.PropagatedContext")
PropagatedContextElement = java.type("io.micronaut.core.propagation.PropagatedContextElement")
ReactorPropagation = java.type("io.micronaut.core.async.propagation.ReactorPropagation")


@Requires(property="spec.name", value="PropagatedContextSpec")
# tag::example[]
@Controller
class HelloController:

    @Get("/hello")
    def hello(self, name: Annotated[str, QueryValue("name")]):
        propagatedContext = PropagatedContext.get().plus(MyContextElement(name))  # <1>
        return Mono.just("Hello, " + name) \
            .contextWrite(lambda ctx: ReactorPropagation.addPropagatedContext(ctx, propagatedContext))  # <2>


@dataclass
class MyContextElement(PropagatedContextElement):
    value: str
# end::example[]
