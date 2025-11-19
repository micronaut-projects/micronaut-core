from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from micronaut.context.exceptions import NoSuchBeanException
from jakarta.inject import Inject
from typing import Annotated
from .NotNullExample import NotNullExample
import java

# tag::class[]
@MicronautTest
class AroundSpec:
    context : Annotated[ApplicationContext, Inject] = None

    # tag::test[]
    @Test
    def test_around(self, example : NotNullExample):
        try:
            example.doWork(None)
            assert False, "Should have failed"
        except Exception as e:
            assert str(e) == 'Null parameter [taskName] is not allowed'
    # end::test[]


