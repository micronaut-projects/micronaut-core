from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .PythonGreeter import PythonGreeter


@MicronautTest
class PythonGreeterSpec:
    greeter: Annotated[PythonGreeter, Inject]

    @Test
    def javaReachesThePythonOverride(self):
        greeter = self.greeter
        before = greeter.count()
        assert greeter.greet() == f"HELLO, PYTHON ({before + 1})"
        assert greeter.count() == before + 1

    @Test
    def pythonReachesTheJavaBase(self):
        greeter = self.greeter
        before = greeter.count()
        assert greeter.greet_twice() == f"HELLO, PYTHON ({before + 1}) HELLO, PYTHON ({before + 2})"
        assert greeter.count() == before + 2
