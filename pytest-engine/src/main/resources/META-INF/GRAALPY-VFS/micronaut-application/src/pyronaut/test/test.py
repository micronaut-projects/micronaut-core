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
    return ApplicationContextWrapper(extension.getContext())


def to_java_array(list):
    StringArray = java.type("java.lang.String[]")
    arr = StringArray(len(list))
    for i, name in enumerate(list):
        arr[i] = name

    return arr

class ApplicationContextWrapper:
    def __init__(self, java_app_context):
        """
        :param java_app_context: The Java ApplicationContext instance
                                 (accessed via polyglot, e.g., polyglot_context.eval("java", "new your.package.ApplicationContext()"))
        """
        self.java_ctx = java_app_context

    def __getitem__(self, key):
        """
        Supports ctx["Foo"] notation.
        Delegates to the Java method (e.g., getBean(String)) and raises KeyError if not found.
        """
        if isinstance(key, str):
            result = self.java_ctx.getBean(java.type(key))
            if result is None:
                raise KeyError(f"Key '{key}' not found in context")

            if hasattr(result, 'asPolyglotValue'):
                 return result.asPolyglotValue()
            else:
                return result
        elif isinstance(key, type):
            # Class key: Resolve FQN as "module.qualname"
            module_name = key.__module__
            qualname = key.__qualname__  # Handles nested classes if needed
            class_name = key.__name__  # Simple class name for duplicate check
            if module_name == '__main__' or module_name.startswith('__'):
                # Handle builtin or main-module classes; customize as needed (e.g., raise error or use qualname only)
                raise ValueError(f"Cannot resolve FQN for class '{key.__name__}' in module '{module_name}'")
            # Base FQN
            fqn = f"{module_name}.{qualname}"

            # Heuristic to strip duplicate if module ends with '.ClassName'
            if module_name.endswith(f'.{class_name}'):
                # Strip the trailing '.ClassName' from module and reconstruct
                stripped_module = module_name[:-len(f'.{class_name}')]
                fqn = f"{stripped_module}.{qualname}" if stripped_module else qualname

            lookup_key = fqn
            result = self.java_ctx.getBean(java.type(lookup_key))
            if result is None:
                raise KeyError(f"Key '{key}' not found in context")

            if hasattr(result, 'asPolyglotValue'):
                return result.asPolyglotValue()
            else:
                return result
        else:
            raise TypeError(f"Unsupported key type: {type(key)}")

    def __getattr__(self, name):
        """
        Delegates all other attribute/method accesses to the Java ApplicationContext.
        This enables transparent proxying: wrapper.getBeanCount() -> java_ctx.getBeanCount()
        Avoids infinite recursion by not delegating Python dunder methods (e.g., __getattr__ itself).
        """
        if name.startswith("__") and name.endswith("__"):
            # Skip Python special methods to prevent recursion or unexpected behavior
            raise AttributeError(f"'{type(self).__name__}' object has no attribute '{name}'")

        # Forward to Java object
        attr = getattr(self.java_ctx, name, None)
        if attr is None:
            raise AttributeError(f"'{type(self).__name__}' object has no attribute '{name}' (not found on Java context)")
        return attr

    # Optional: Add other Pythonic methods for completeness (these override delegation if needed)
    def __contains__(self, key):
        """Supports 'if "Foo" in ctx:'"""
        try:
            self[key]  # Will raise KeyError if missing
            return True
        except KeyError:
            return False

    def get(self, key, default=None):
        """Python dict-like get() for optional default"""
        try:
            return self[key]
        except KeyError:
            return default

# Export the function for use in pytest fixtures
__all__ = ['MicronautTest', 'micronaut_test_fixture']
