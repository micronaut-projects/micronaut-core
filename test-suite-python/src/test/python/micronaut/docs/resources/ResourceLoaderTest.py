from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .MyResourceLoader import MyResourceLoader


@Property(name="spec.name", value="ResourceLoaderTest")
@MicronautTest
class ResourceLoaderTest:
    myResourceLoader: Annotated[MyResourceLoader, Inject]

    @Test
    @Disabled("Python docs test classpath resources are not resolved through ResourceResolver yet")
    def testExampleForResourceResolver(self) -> None:
        text = self.myResourceLoader.getClasspathResourceAsText("hello.txt")
        assert text.isPresent()
        assert text.get().strip() == "Hello!"
