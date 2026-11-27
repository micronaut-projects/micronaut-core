from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.test.support import TestPropertyProvider
from org.junit.jupiter.api import Test, TestInstance

from .ProvidedGreeter import ProvidedGreeter


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestPropertyProviderTest(TestPropertyProvider):
    """A Python test can provide the properties of its application context.

    The JUnit extension calls ``getProperties()`` before the application context, and with it the
    GraalPy runtime of the application, exists. The Python object of the test is created in a
    GraalPy context built for the occasion, which the application context adopts as its primary
    context once it starts: the test keeps the state ``getProperties()`` left on it, and the beans
    injected into it are Python objects of the same context.
    """

    greeting: Annotated[str, Property(name="provided.greeting")]
    greeter: Annotated[ProvidedGreeter, Inject]

    def getProperties(self) -> dict[str, str]:
        self.provided_calls = getattr(self, "provided_calls", 0) + 1
        return {"provided.greeting": "Hello"}

    @Test
    def the_provided_properties_configure_the_application(self) -> None:
        assert self.greeting == "Hello"
        assert self.greeter.greet("John") == "Hello John"

    @Test
    def the_test_that_provided_the_properties_runs_the_tests(self) -> None:
        assert self.provided_calls == 1

    @Test
    def the_injected_beans_are_python_objects_of_the_same_context(self) -> None:
        assert isinstance(self.greeter, ProvidedGreeter), f"not a ProvidedGreeter: {type(self.greeter)}"
