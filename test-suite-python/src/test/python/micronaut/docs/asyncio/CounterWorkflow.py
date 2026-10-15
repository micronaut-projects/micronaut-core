from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .StatefulCounter import StatefulCounter


# A singleton without coroutine methods between the controller and the counter, as an application service
# layer is: it must reach the one counter, with the counter's Java dependency.
@Requires(property="spec.name", value="PythonAsyncSingletonSpec")
@Singleton
class CounterWorkflow:

    def __init__(self, counter: StatefulCounter):
        self.counter = counter

    def counter_service(self) -> StatefulCounter:
        return self.counter
