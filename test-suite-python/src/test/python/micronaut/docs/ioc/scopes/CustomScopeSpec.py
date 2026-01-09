from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated
import java

@MicronautTest
class CustomScopeSpec:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_custom_scope(self):
        Foo = java.type("micronaut.docs.ioc.scopes.Foo")
        foo1 = self.context.getBean(Foo).asPolyglotValue()
        assert foo1 is not None

