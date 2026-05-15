from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import Pattern
from micronaut.context import ApplicationContext
from micronaut.context.annotation import ConfigurationProperties, Context, Requires
from micronaut.context.exceptions import BeanInstantiationException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


def _exception_messages(exception):
    current = exception
    for _ in range(16):
        if current is None:
            break
        message = current.getMessage()
        if message:
            yield message
        current = current.getCause()


@Requires(property="spec.name", value="PythonConfigurationValidationSpec")
@Context
@ConfigurationProperties("framework")
@dataclass
class FrameworkConfiguration:
    language: Annotated[
        str | None,
        Pattern(regexp="groovy|java|kotlin|python"),
    ] = None


@MicronautTest(startApplication=False)
class PythonConfigurationValidationSpec:
    @Test
    def test_context_configuration_properties_are_validated_on_startup(self):
        try:
            ApplicationContext.run(
                {
                    "spec.name": "PythonConfigurationValidationSpec",
                    "framework.language": "scala",
                },
                "test",
            )
        except BeanInstantiationException as e:
            assert any(
                'language - must match "groovy|java|kotlin|python"' in message
                for message in _exception_messages(e)
            )
        else:
            assert False
