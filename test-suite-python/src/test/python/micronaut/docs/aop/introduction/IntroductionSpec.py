from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from micronaut.context.exceptions import NoSuchBeanException
from jakarta.inject import Inject
from typing import Annotated
from .StubExample import StubExample

# tag::class[]
@MicronautTest
class IntroductionSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Disabled("Introduction advice not yet supported")
    # tag::test[]
    @Test
    def test_introduction(self, example : StubExample):
        assert example.get_number() == 10, "Should be 10"
        assert example.get_date() is None, "Should be none"
    # end::test[]


