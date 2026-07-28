from dataclasses import dataclass
from typing import Annotated

import java
from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.core.async_.propagation import ReactorPropagation
from micronaut.http.annotation import Controller, Get, QueryValue
# end::imports[]

Mono = java.type("reactor.core.publisher.Mono")
PropagatedContext = java.type("io.micronaut.core.propagation.PropagatedContext")
PropagatedContextElement = java.type("io.micronaut.core.propagation.PropagatedContextElement")


@Requires(property="spec.name", value="PropagatedContextSpec")
# tag::example[]
@Controller
class HelloController:

    @Get("/hello")
    def hello(self, name: Annotated[str, QueryValue("name")]) -> Mono[str]:
        propagatedContext = PropagatedContext.get().plus(MyContextElement(name))  # <1>
        return Mono.just("Hello, " + name) \
            .contextWrite(lambda ctx: ReactorPropagation.addPropagatedContext(ctx, propagatedContext))  # <2>


@dataclass
class MyContextElement(PropagatedContextElement):
    value: str
# end::example[]
