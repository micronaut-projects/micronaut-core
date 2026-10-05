import builtins

from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get, Post

from .CounterWorkflow import CounterWorkflow


@Requires(property="spec.name", value="PythonAsyncSingletonSpec")
@Controller("/async-singleton")
class AsyncSingletonController:

    def __init__(self, workflow: CounterWorkflow):
        self.workflow = workflow
        self.requests = 0

    @Post("/increment")
    async def increment(self) -> str:
        self.requests += 1
        counter = self.workflow.counter_service()
        return f"{await counter.increment()}:{counter.has_resolver()}"

    @Get("/count")
    def count(self) -> str:
        return f"{self.workflow.counter_service().current()}:{self.requests}"

    @Get("/context-id")
    async def context_id(self) -> str:
        return builtins.__MN_CTX_ID__
