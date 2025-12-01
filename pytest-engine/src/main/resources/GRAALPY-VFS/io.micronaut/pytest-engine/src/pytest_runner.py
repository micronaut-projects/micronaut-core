"""
Main script for running pytest tests in GraalPy environment.

This script sets up the virtual filesystem, loads the pytest plugin,
and executes pytest with the specified test files.
"""

import sys
import os
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

        # Prepare pytest arguments
        pytest_args = test_files.copy()

        # Add quiet mode to reduce output
        pytest_args.extend(['-q', '--tb=short'])

        # Add our plugin
        pytest_args.extend(['-p', 'no:warnings'])  # Disable warnings plugin

        # Run pytest with our plugin
        exit_code = pytest.main(pytest_args, plugins=[plugin])

        return exit_code

    except ImportError as e:
        print(f"Failed to import pytest: {e}", file=sys.stderr)
        raise
    except Exception as e:
        print(f"Error running pytest: {e}", file=sys.stderr)
        raise


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
