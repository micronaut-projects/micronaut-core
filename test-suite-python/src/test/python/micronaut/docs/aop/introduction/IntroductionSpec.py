from org.junit.jupiter.api import Test
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
    # tag::test[]
    example : Annotated[StubExample, Inject] = None

    @Test
    def test_introduction(self):
        assert self.example.get_number() == 10, "Should be 10"
        assert self.example.get_date() is None, "Should be none"
    # end::test[]


