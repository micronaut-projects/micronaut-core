"""
Main script for running pytest tests in GraalPy environment.

This script sets up the virtual filesystem, loads the pytest plugin,
and executes pytest with the specified test files.
"""

import sys
import os
import java
from pathlib import Path
from typing import List, Optional, Any


def setup_virtual_filesystem():
    """
    Set up the virtual filesystem to include both src/main/python and src/test/python directories.
    """
    # Get the current working directory (project root)
    project_root = Path.cwd()

    # Add src/main/python to Python path if it exists
    main_python_dir = project_root / "src" / "main" / "python"
    if main_python_dir.exists():
        sys.path.insert(0, str(main_python_dir))

    # Add src/test/python to Python path if it exists
    test_python_dir = project_root / "src" / "test" / "python"
    if test_python_dir.exists():
        sys.path.insert(0, str(test_python_dir))


def run_pytest(test_files: List[str], listener: Any):
    """
    Run pytest with the specified test files and listener.

    Args:
        test_files: List of test file paths to run
        listener: Java object implementing PytestTestListener interface
    """
    try:
        # Import pytest here to ensure virtual filesystem is set up
        import pytest
        from pytest_listener import create_plugin

        # Create the plugin with the Java listener
        plugin = create_plugin(listener)

        # Set sys.argv to avoid argument parsing issues in pytest
        # pytest tries to access sys.argv[0] for the program name
        original_argv = sys.argv[:]
        sys.argv = ['pytest'] + test_files

        try:
            # Use pytest.main() with plugins - this should work now that sys.argv is set
            exit_code = pytest.main(test_files, plugins=[plugin])
            return exit_code
        finally:
            # Restore original sys.argv
            sys.argv = original_argv

    except Exception as e:
        print(f"Error in simulated pytest execution: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        raise


def extract_test_functions(file_path: str) -> List[str]:
    """
    Extract test function names from a Python file.
    This is a simple simulation - in real pytest it would use AST parsing.
    """
    try:
        with open(file_path, 'r') as f:
            content = f.read()

        # Simple regex to find test functions (very basic)
        import re
        test_functions = []
        for line in content.split('\n'):
            match = re.match(r'def\s+(test_\w+)\s*\(', line.strip())
            if match:
                test_functions.append(match.group(1))

        return test_functions

    except Exception as e:
        print(f"Error extracting test functions from {file_path}: {e}", file=sys.stderr)
        return []


def main():
    """
    Main entry point for the pytest runner.
    """
    # Set up virtual filesystem
    setup_virtual_filesystem()

    # Get arguments from command line
    if len(sys.argv) < 2:
        print("Usage: pytest_runner.py <listener> [test_files...]", file=sys.stderr)
        sys.exit(1)

    # First argument should be the Java listener object
    # In GraalPy, this will be passed as a polyglot value
    listener = sys.argv[1]

    # Remaining arguments are test files
    test_files = sys.argv[2:] if len(sys.argv) > 2 else []

    if not test_files:
        print("No test files specified", file=sys.stderr)
        sys.exit(1)

    # Run pytest
    exit_code = run_pytest(test_files, listener)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
