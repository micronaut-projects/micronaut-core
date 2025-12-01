"""
Sample pytest test file to demonstrate the pytest-engine functionality.
"""


def test_simple_assertion():
    """A simple test that should pass."""
    assert 1 + 1 == 2


def test_string_operations():
    """Test basic string operations."""
    text = "hello world"
    assert text.startswith("hello")
    assert text.endswith("world")
    assert len(text) == 11


def test_list_operations():
    """Test list operations."""
    numbers = [1, 2, 3, 4, 5]
    assert len(numbers) == 5
    assert sum(numbers) == 15
    assert 3 in numbers


def test_failing_test():
    """This test should fail to demonstrate error handling."""
    assert 1 + 1 == 3, "This assertion should fail"

