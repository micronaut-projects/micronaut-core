from micronaut.context import ApplicationContext
from micronaut.context.env import Environment
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class DefaultEnvironmentSpec:
    # tag::disableEnvDeduction[]
    @Test
    def test_disable_environment_deduction_via_builder(self) -> None:
        ctx = (
            ApplicationContext.builder()
            .deduceEnvironment(False)
            .properties({"micronaut.server.port": -1})
            .start()
        )
        assert not ctx.getEnvironment().getActiveNames().contains(Environment.TEST)
        ctx.close()
    # end::disableEnvDeduction[]
