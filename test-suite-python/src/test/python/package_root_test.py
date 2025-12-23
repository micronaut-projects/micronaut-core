"""
Example Python application for testing Micronaut Python compilation.
"""
from jakarta.inject import Singleton, Named
from micronaut.context.annotation import Executable

@Singleton
@Named("exampleService")
class RootExampleService:
    @Executable
    def say_hello(self) -> str:
        return "Hello from Python service!"


from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

@MicronautTest
class RootExampleTest:
    @Test
    def test_get_message(self, example_service : RootExampleService) -> None:
        # fix this in JUnit
        msg = example_service.say_hello()
        assert msg is not None

