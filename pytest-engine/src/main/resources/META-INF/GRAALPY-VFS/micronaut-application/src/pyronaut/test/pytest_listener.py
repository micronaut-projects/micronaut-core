"""
Pytest plugin for Micronaut pytest-engine integration.

This plugin hooks into pytest's event system to communicate test execution
events back to Java code via the PytestTestListener interface.
"""

import pytest
from typing import Optional, Any
import sys
import traceback
import java
import inspect
from typing import get_origin, get_args

class MicronautPytestPlugin:
    """
    Pytest plugin that communicates with Java PytestTestListener.
    """

    def __init__(self, listener: Any):
        """
        Initialize the plugin with a Java listener object.

        Args:
            listener: Java object implementing PytestTestListener interface
        """
        self.listener = listener
        self.current_file = None
        self.test_results = {}  # Store test results by test id

    def pytest_sessionstart(self, session):
        """Called when pytest session starts."""
        pass

    def pytest_sessionfinish(self, session, exitstatus):
        """Called when pytest session finishes."""
        TestExecutionResult = java.type("org.junit.platform.engine.TestExecutionResult")
        RuntimeException = java.type("java.lang.RuntimeException")

        # Convert pytest exit status to JUnit TestExecutionResult
        if exitstatus == pytest.ExitCode.OK:
            result = TestExecutionResult.successful()
        else:
            # Create a failed result with exit status information
            exception = RuntimeException(f"Pytest session failed with exit code: {exitstatus}")
            result = TestExecutionResult.failed(exception)

        self.listener.onResult(result)

    def pytest_collectstart(self, collector):
        """Called when collection starts for a file."""
        if hasattr(collector, 'fspath') and collector.fspath:
            self.current_file = collector.fspath
            self.listener.beforeFile(f"{collector.fspath}")

    def pytest_runtest_call(self, item):
        """Called when test is actually executed."""
        test_id = self._get_test_id(item)
        self.listener.beforeTest(test_id, item)

    def pytest_runtest_makereport(self, item, call):
        """Called when test report is created."""
        test_id = self._get_test_id(item)

        # Store the test result for later use in teardown
        if call.excinfo is not None:
            # Test failed
            self.test_results[test_id] = call.excinfo.value
        else:
            # Test passed
            self.test_results[test_id] = None

    def pytest_runtest_teardown(self, item):
        """Called after test teardown."""
        TestExecutionResult = java.type("org.junit.platform.engine.TestExecutionResult")
        AssertionError = java.type("io.micronaut.test.pytest.PythonAssertionError")

        test_id = self._get_test_id(item)
        exception = self.test_results.get(test_id)

        if exception is not None:
            result = TestExecutionResult.failed(AssertionError(f"{exception}"))
        else:
            result = TestExecutionResult.successful()

        self.listener.afterTest(test_id, item, result)

        # Clean up stored result
        self.test_results.pop(test_id, None)

    def pytest_collectreport(self, report):
        """Called when collection report is generated."""
        TestExecutionResult = java.type("org.junit.platform.engine.TestExecutionResult")
        if report.failed:
            AssertionError = java.type("io.micronaut.test.pytest.PythonAssertionError")
            exception = AssertionError(f"Collection failed: {report.longrepr}")
            result = TestExecutionResult.failed(exception)
            if self.current_file:
                self.listener.afterFile(f"{self.current_file}", result)
                raise Exception(f"Collection failed: {report.longrepr}")
        else:
            if self.current_file:
                self.listener.afterFile(f"{self.current_file}", TestExecutionResult.successful())

    def _resolve_bean_for_type(self, context, python_type):
        """Resolve a bean based on Python type hint."""
        if self._is_list_type(python_type):
            # Handle List[SomeType]
            element_type = get_args(python_type)[0]
            java_class_name = self._python_type_to_java_class(element_type)
            if java_class_name:
                try:
                    java_class = java.type(java_class_name)
                    collection = context.getBeansOfType(java_class)
                    return list(collection)  # Convert to Python list
                except:
                    pass
        elif self._is_dict_type(python_type):
            # Handle Dict[str, SomeType]
            key_type, value_type = get_args(python_type)
            if key_type == str:
                java_class_name = self._python_type_to_java_class(value_type)
                if java_class_name:
                    try:
                        java_class = java.type(java_class_name)
                        java_map = context.mapOfType(java_class)
                        return dict(java_map)  # Convert to Python dict
                    except:
                        pass
        else:
            # Handle single bean
            java_class_name = self._python_type_to_java_class(python_type)
            if java_class_name:
                try:
                    return context.getBean(java.type(java_class_name))
                except:
                    pass

        return None

    def _is_list_type(self, python_type):
        """Check if type is List[T]."""
        return get_origin(python_type) is list

    def _is_dict_type(self, python_type):
        """Check if type is Dict[K, V]."""
        return get_origin(python_type) is dict

    def _python_type_to_java_class(self, python_type):
        """Convert Python type to fully qualified Java class name."""
        if hasattr(python_type, '__module__') and hasattr(python_type, '__qualname__'):
            module = python_type.__module__
            qualname = python_type.__qualname__
            return f"{module}.{qualname}"
        return None

    def _get_test_id(self, item) -> str:
        """
        Generate a unique test ID from pytest item.

        Args:
            item: pytest test item

        Returns:
            Unique test identifier string
        """
        if hasattr(item, 'nodeid'):
            return item.nodeid
        else:
            # Fallback for older pytest versions
            return f"{item.parent.name}::{item.name}"


def create_plugin(listener: Any) -> MicronautPytestPlugin:
    """
    Factory function to create the pytest plugin.

    Args:
        listener: Java object implementing PytestTestListener interface

    Returns:
        Configured MicronautPytestPlugin instance
    """
    return MicronautPytestPlugin(listener)
