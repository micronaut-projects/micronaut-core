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

    def pytest_runtest_setup(self, item):
        """Called before test setup."""
        test_id = self._get_test_id(item)
        self.listener.beforeTest(test_id)

    def pytest_runtest_call(self, item):
        """Called when test is actually executed."""
        pass

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
        RuntimeException = java.type("java.lang.RuntimeException")

        test_id = self._get_test_id(item)
        exception = self.test_results.get(test_id)

        if exception is not None:
            result = TestExecutionResult.failed(RuntimeException(f"{exception}"))
        else:
            result = TestExecutionResult.successful()

        self.listener.afterTest(test_id, result)

        # Clean up stored result
        self.test_results.pop(test_id, None)

    def pytest_collectreport(self, report):
        """Called when collection report is generated."""
        if report.failed:
            TestExecutionResult = java.type("org.junit.platform.engine.TestExecutionResult")
            RuntimeException = java.type("java.lang.RuntimeException")

            exception = RuntimeException(f"Collection failed: {report.longrepr}")
            result = TestExecutionResult.failed(exception)
            if self.current_file:
                self.listener.afterFile(self.current_file, result)

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
