"""
Unit tests for micronaut_transformer.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest
from unittest import mock

from micronaut_transformer import MicronautTransformer, MicronautRuntimeTransformer, ast_equal, unparse


def no_class_element(name):
    return None


def no_class_elements(package):
    return []


class FakeClassElement:
    def __init__(self, name):
        self.name = name

    def getName(self):
        return self.name

    def getSimpleName(self):
        return self.name.split('.')[-1]


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

    def test_unresolvable_io_imports_are_reported(self):
        source = (
            "from io.swagger.v3.oas.annotations import Operation\n"
            "from io.nosuch.annotations import *\n"
            "import io.nosuch.other as other\n"
            "import io.nosuch.alias\n"
            "from io import StringIO\n"
            "import io\n"
        )
        transformer = MicronautTransformer(no_class_element, no_class_elements)
        transformer.visit(ast.parse(source))
        errors = transformer.validation_errors
        self.assertEqual(4, len(errors), errors)
        self.assertIn("Cannot resolve Java import [io.swagger.v3.oas.annotations.Operation]", errors[0])
        self.assertIn("Cannot resolve Java package [io.nosuch.annotations]", errors[1])
        self.assertIn("Cannot resolve Java package [io.nosuch.other]", errors[2])
        self.assertIn("Cannot resolve Java package [io.nosuch.alias]", errors[3])

    def test_relative_import_of_an_application_io_package_is_not_a_java_import(self):
        source = "from .io.util import helper\nfrom ..io import util\nfrom . import io\n"
        transformer = MicronautTransformer(no_class_element, no_class_elements)
        transformed = transformer.visit(ast.parse(source))
        self.assertEqual([], transformer.validation_errors)
        self.assertTrue(ast_equal(ast.parse(source), transformed))

    def test_java_io_package_import_without_alias_is_reported(self):
        transformer = MicronautTransformer(no_class_element, lambda package: [FakeClassElement(package + ".Hidden")])
        with mock.patch.object(MicronautTransformer, "_is_annotation_class", return_value=False):
            transformer.visit(ast.parse("import io.swagger.v3.oas.annotations\nimport io.swagger.v3.oas.annotations as oas\n"))
        errors = transformer.validation_errors
        self.assertEqual(1, len(errors), errors)
        self.assertIn(
            "Java package import [import io.swagger.v3.oas.annotations] requires an alias such as "
            "[import io.swagger.v3.oas.annotations as annotations]",
            errors[0],
        )

    def test_io_annotation_clashing_with_a_generated_decorator_is_reported_with_an_alias_hint(self):
        transformer = MicronautTransformer(FakeClassElement, no_class_elements)
        # as left behind by ``from micronaut.http.annotation import *``
        transformer.generated_decorators.add("Header")
        with mock.patch.object(MicronautTransformer, "_is_annotation_class", return_value=True):
            transformer.visit(ast.parse("from io.swagger.v3.oas.annotations.headers import Header\n"))
        errors = transformer.validation_errors
        self.assertEqual(1, len(errors), errors)
        self.assertIn("Java import [io.swagger.v3.oas.annotations.headers.Header] clashes with the decorator [Header]", errors[0])
        self.assertIn("[from io.swagger.v3.oas.annotations.headers import Header as SwaggerHeader]", errors[0])


class RuntimeImportRewriteTest(unittest.TestCase):
    def rewrite(self, source):
        return unparse(MicronautRuntimeTransformer(no_class_element, no_class_elements).visit(ast.parse(source)))

    def test_io_from_imports_are_rewritten_to_generated_packages(self):
        code = self.rewrite(
            "from io.micronaut.context.annotation import Executable\n"
            "from io.swagger.v3.oas.annotations import Operation as Op\n"
            "from io.swagger.v3.oas.annotations.media import *\n"
            "from io.kubernetes.client.openapi.models import V1Pod\n"
            "from io.micronaut.async_.support import Helper\n"
        )
        self.assertIn("from micronaut.context.annotation import Executable", code)
        self.assertIn("from swagger.v3.oas.annotations import Operation as Op", code)
        self.assertIn("from swagger.v3.oas.annotations.media import *", code)
        self.assertIn("from kubernetes.client.openapi.models import V1Pod", code)
        self.assertIn("from micronaut.async_.support import Helper", code)
        self.assertNotIn("from io.", code)

    def test_io_module_imports_are_rewritten_to_generated_packages(self):
        code = self.rewrite(
            "import io.swagger.v3.oas.annotations as oas, io.micronaut.http.annotation as http\n"
        )
        self.assertIn("import swagger.v3.oas.annotations as oas, micronaut.http.annotation as http", code)

    def test_python_io_module_imports_are_untouched(self):
        source = "import io\nfrom io import StringIO\nimport io as pyio\nfrom .io.util import helper\nfrom ..io import util\n"
        self.assertTrue(ast_equal(ast.parse(source), MicronautRuntimeTransformer(no_class_element, no_class_elements).visit(ast.parse(source))))


if __name__ == "__main__":
    unittest.main()
