from org.junit.jupiter.api import Disabled, Test

from micronaut.test.extensions.junit5.annotation import MicronautTest


@MicronautTest
class AdviceFactorySpec:
    @Test
    @Disabled("Python AOP advice on @Factory bean methods fails during factory bean processing")
    def test_aop_advice_on_factory_beans(self):
        pass
