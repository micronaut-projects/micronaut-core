from dataclasses import dataclass
from typing import Annotated

from micronaut.context.python.scope import ContextPooled
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import Body, Controller, Post


@Introspected
@dataclass
class BenchmarkOrder:
    customer: str
    product: str
    quantity: int
    priority: bool
    note: str


@ContextPooled
@Controller("/python-request-body-benchmark")
class PythonRequestBodyBenchmarkController:
    @Post("/body")
    def body(self, order: Annotated[BenchmarkOrder, Body]) -> str:
        return f"{order.customer}:{order.product}:{order.quantity}:{order.priority}:{order.note}"

    @Post("/control")
    def control(self) -> str:
        return "control"
