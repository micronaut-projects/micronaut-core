"""
Unit tests for micronaut_transformer.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest

from micronaut_transformer import MicronautTransformer, MicronautRuntimeTransformer, ast_equal, unparse


def no_class_element(name):
    return None


def no_class_elements(package):
    return []


class AstEqualTest(unittest.TestCase):
    def test_ignores_positions_but_not_structure(self):
        left = ast.parse("x = 1\n\ndef f(a):\n    return a\n")
        right = ast.parse("x = 1\ndef f(a):\n        return a")
        self.assertTrue(ast_equal(left, right))
        self.assertFalse(ast_equal(left, ast.parse("x = 2\ndef f(a):\n    return a")))
        self.assertFalse(ast_equal(left, ast.parse("x = 1\ndef f(a, b):\n    return a")))
        self.assertFalse(ast_equal(left, ast.parse("x = 1")))

    def test_agrees_with_dump_comparison(self):
        sources = [
            "import os\nclass A:\n    pass\n",
            "from a import b\n@b\nclass C:\n    x: int = 1\n",
            "def f():\n    return [1, 2, (3, 4)]\n",
        ]
        for source in sources:
            tree = ast.parse(source)
            other = ast.parse(source)
            self.assertEqual(
                ast.dump(tree, include_attributes=False) == ast.dump(other, include_attributes=False),
                ast_equal(tree, other),
            )
            other.body.append(ast.Pass())
            self.assertEqual(
                ast.dump(tree, include_attributes=False) == ast.dump(other, include_attributes=False),
                ast_equal(tree, other),
            )


class RuntimeTransformerTest(unittest.TestCase):
    def test_source_without_java_constructs_is_unchanged(self):
        source = "class Plain:\n    def hello(self):\n        return 'hi'\n"
        pristine = ast.parse(source)
        transformed = MicronautRuntimeTransformer(no_class_element, no_class_elements).visit(ast.parse(source))
        self.assertTrue(ast_equal(pristine, transformed))

    def test_build_dependency_calls_are_removed(self):
        source = "from pyronaut.build import Dependency\nDependency('org:artifact:1.0')\nclass Plain:\n    pass\n"
        pristine = ast.parse(source)
        transformed = MicronautRuntimeTransformer(no_class_element, no_class_elements).visit(ast.parse(source))
        self.assertFalse(ast_equal(pristine, transformed))
        code = unparse(transformed)
        self.assertNotIn("Dependency", code)
        self.assertIn("class Plain", code)


class TransformerTest(unittest.TestCase):
    def test_module_docstring_detection(self):
        transformer = MicronautTransformer(no_class_element, no_class_elements)
        self.assertTrue(transformer._is_module_docstring(ast.parse("'''docs'''").body[0]))
        self.assertFalse(transformer._is_module_docstring(ast.parse("x = 'docs'").body[0]))
        self.assertFalse(transformer._is_module_docstring(ast.parse("42").body[0]))

    def test_keyword_segments_round_trip_between_python_and_java_names(self):
        transformer = MicronautTransformer(no_class_element, no_class_elements)
        self.assertEqual("io.micronaut.async.support", transformer._to_java_import_module("io.micronaut.async_.support"))
        self.assertEqual("io.micronaut.async_.support", transformer._to_python_import_module("io.micronaut.async.support"))
        self.assertEqual("jakarta.inject", transformer._to_java_import_module("jakarta.inject"))


if __name__ == "__main__":
    unittest.main()
