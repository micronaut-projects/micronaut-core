from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.docs.aop.advice.MyBean import MyBean
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class AdviceFactorySpec:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_aop_advice_on_factory_beans(self):
        beans = self.context.getBeansOfType(MyBean)

        assert len(beans) == 2
        assert all(bean.do_work() == "Done" for bean in beans)
