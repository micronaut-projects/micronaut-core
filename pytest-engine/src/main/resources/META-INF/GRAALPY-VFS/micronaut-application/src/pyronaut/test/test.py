"""
Micronaut test support for pytest.

This module provides pytest fixtures for testing Micronaut applications.
"""

import sys
import java
from typing import Optional, Dict, Any, List, Union

class MicronautTest:
    """
    Value object representing Micronaut test configuration.

    Similar to the Java MicronautTestValue used in JUnit5 extension.
    """

    def __init__(self,
                 environments: List[str] = ["test"],
                 packages: List[str] = [],
                 transactional: bool = False,
                 rollback: bool = True,
                 rebuild_context: bool = False,
                 start_application: bool = True,
                 resolve_parameters: bool = True,
                 context_builder: Any = None,
                 properties: Dict[str, Any] = {}):
        self.environments = environments or []
        self.packages = packages or []
        self.transactional = transactional
        self.rollback = rollback
        self.rebuild_context = rebuild_context
        self.start_application = start_application
        self.resolve_parameters = resolve_parameters
        self.context_builder = context_builder
        self.properties = properties or {}


# Convenience function for pytest fixtures
def micronaut_test_fixture(request,
                            micronaut_test: MicronautTest = None):
    """
    Create a MicronautTest configuration for pytest fixtures.

    This is a convenience function that can be used directly in pytest fixtures:

    @pytest.fixture
    def my_context(request):
        fixture = micronaut_test_fixture(request, MicronautTest(environments="test", transactional=False,
                                                                properties={"custom.property": "test_value"}))
        yield fixture
        fixture.stop()
    """

    if micronaut_test is None:
        micronaut_test = MicronautTest()

    PytestMicronautExtension = java.type("io.micronaut.test.pytest.extension.PytestMicronautExtension")
    MicronautTestValue = java.type("io.micronaut.test.annotation.MicronautTestValue")
    extension = PytestMicronautExtension(micronaut_test.properties, request.node)
    test_value = MicronautTestValue(
        None, # application
        to_java_array(micronaut_test.environments),
        to_java_array(micronaut_test.packages),
        None, # propertySources
        micronaut_test.rollback,
        micronaut_test.transactional,
        micronaut_test.rebuild_context,
        None, # contextBuilder
        None, # transactionMode
        micronaut_test.start_application,
        micronaut_test.resolve_parameters
    )

    try:
        extension.beforeClass(request.node.__module__, PytestMicronautExtension, test_value)
    except BaseException as e:
        raise Exception(f"Micronaut Fixture Setup Failed: {e}")
    return extension.getContext()


def to_java_array(list):
    StringArray = java.type("java.lang.String[]")
    arr = StringArray(len(list))
    for i, name in enumerate(list):
        arr[i] = name

    return arr

# Export the function for use in pytest fixtures
__all__ = ['MicronautTest', 'micronaut_test_fixture']
