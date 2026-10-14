from typing import Annotated

from java.io import IOException
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .QueueInitializer import QueueInitializer


@MicronautTest
class QueueInitializerSpec:
    initializer: Annotated[QueueInitializer, Inject]

    @Test
    def theOverrideDelegatesToTheJavaBase(self):
        assert self.initializer.initializeAll(["orders", "invoices"]) == "ok"
        assert list(self.initializer.declared()) == ["main/queue:orders", "main/queue:invoices"]

    @Test
    def aCheckedExceptionRaisedInPythonReachesTheJavaCaller(self):
        assert self.initializer.initializeAll(["reserved.events"]) == "failed: reserved.events is reserved"

    @Test
    def aCheckedExceptionOfTheJavaBaseReachesPython(self):
        try:
            self.initializer.initialize("", "orders")
            assert False, "expected an IOException"
        except IOException as e:
            assert e.getMessage() == "No channel for queue:orders"
