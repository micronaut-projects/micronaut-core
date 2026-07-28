import java
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from micronaut.context import ApplicationContext
from micronaut.context.env import PropertySource


@MicronautTest
class EnvironmentSpec:
    @Test
    def test_run_environment(self) -> None:
        # tag::env[]
        application_context = ApplicationContext.run("test", "android")
        environment = application_context.getEnvironment()

        assert environment.getActiveNames().contains("test")
        assert environment.getActiveNames().contains("android")
        # end::env[]

    @Test
    def test_run_environment_with_properties(self) -> None:
        # tag::envProps[]
        application_context = ApplicationContext.run(
            PropertySource.of(
                "test",
                {
                    "micronaut.server.host": "foo",
                    "micronaut.server.port": 8080,
                },
            ),
            "test",
            "android",
        )
        environment = application_context.getEnvironment()

        assert environment.getProperty(
            "micronaut.server.host",
            java.type("java.lang.String"),
        ).orElse("localhost") == "foo"
        # end::envProps[]
