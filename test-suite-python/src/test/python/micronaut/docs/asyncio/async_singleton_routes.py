import builtins
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Requires
from micronaut.http.annotation import Get, Post

from .StatefulCounter import StatefulCounter

counter: Annotated[StatefulCounter, Inject]


# A route module is pooled: its coroutines run in the event-loop context, and await the singleton in its own.
@Requires(property="spec.name", value="PythonAsyncSingletonSpec")
@Post("/async-singleton-route/increment")
async def async_singleton_route_increment() -> str:
    return f"{await counter.increment()}:{counter.has_resolver()}"


@Requires(property="spec.name", value="PythonAsyncSingletonSpec")
@Get("/async-singleton-route/context-id")
async def async_singleton_route_context_id() -> str:
    return builtins.__MN_CTX_ID__
