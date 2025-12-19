"""
Test file demonstrating Micronaut test integration with pytest.

This test verifies that the MicronautTest fixture works correctly
and provides access to the ApplicationContext.
"""

import pytest
from pyronaut.test import *
import java
from micronaut.context.env import Environment
from typing import List, Dict
from test import Foo

@pytest.fixture
def my_context(request):
    fixture = micronaut_test_fixture(request, MicronautTest(environments=["foo"], transactional=False,
                                                            properties={"custom.property": "test_value"}))
    yield fixture
    fixture.stop()

@pytest.fixture
def env(my_context):
    return my_context["io.micronaut.context.env.Environment"]

@pytest.fixture
def foo(my_context) -> Foo:
    return my_context[Foo]

def test_micronaut_context_creation(my_context, env: Environment, foo: Foo):
    """
    Test that the Micronaut ApplicationContext is created and accessible.

    This test verifies that the fixture provides access to the context
    and that it's properly configured.
    """
    ctx = my_context

    # Verify the context is created
    assert ctx is not None
    assert env is not None
    assert foo is not None
    assert ctx.isRunning() is True

    # Verify environments are set
    assert "test" in env.getActiveNames()
    assert "test" in ctx.getEnvironment().getActiveNames()
    assert "foo" in ctx.getEnvironment().getActiveNames()


def test_micronaut_context_creation2(my_context):
    """
    Test that the Micronaut ApplicationContext is created and accessible.

    This test verifies that the fixture provides access to the context
    and that it's properly configured.
    """
    ctx = my_context

    # Verify the context is created
    assert ctx is not None
    assert ctx.isRunning() is True

    # Verify environments are set
    assert "test" in ctx.getEnvironment().getActiveNames()
    assert ctx.getEnvironment().containsProperty("custom.property")
    assert ctx.getEnvironment().getProperty("custom.property", java.type("java.lang.String")).get() == "test_value"
