"""
Unit tests for micronaut_transformer.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import os
import tempfile
import unittest
from unittest import mock

import java

from micronaut_transformer import MicronautTransformer, MicronautRuntimeTransformer, ast_equal, unparse

_ClassElement = java.type("io.micronaut.inject.ast.ClassElement")


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


def java_class_element(name):
    """
    A reflective ClassElement for the Java annotation types the tests import, as the compiler answers.
    """
    if name.startswith("micronaut."):
        name = "io." + name
    try:
        return _ClassElement.of(java.type(name))
    except Exception:
        return None


def java_class_elements(package):
    """
    The annotation types of a package, as the compiler answers for a package import.
    """
    if package in ("micronaut.context.annotation", "io.micronaut.context.annotation"):
        return [java_class_element("io.micronaut.context.annotation.Executable")]
    return []


def runtime_source(source, package_name="", source_root=""):
    transformer = MicronautRuntimeTransformer(java_class_element, java_class_elements, None, package_name, source_root)
    return unparse(transformer.visit(ast.parse(source)))


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


class DecoratorFormTest(unittest.TestCase):
    """
    Bare @X and factory @X(...) are told apart by their syntactic form: a bare generated decorator or custom
    annotation function is rewritten to a call, and a call is never mistaken for a bare application.
    """

    def test_bare_generated_decorator_is_called(self):
        code = runtime_source(
            "from micronaut.context.annotation import Executable\n"
            "@Executable\n"
            "class Service:\n"
            "    @Executable\n"
            "    def method(self):\n"
            "        pass\n"
        )
        self.assertEqual(2, code.count("@Executable()"))

    def test_positional_class_argument_stays_an_annotation_value(self):
        source = (
            "from micronaut.context.annotation import Replaces\n"
            "class Orderer:\n"
            "    pass\n"
            "@Replaces(Orderer)\n"
            "class Service:\n"
            "    pass\n"
        )
        self.assertIn("@Replaces(Orderer)", runtime_source(source))

    def test_package_import_decorators_are_called(self):
        code = runtime_source(
            "import micronaut.context.annotation as annotation\n"
            "@annotation.Executable\n"
            "class Service:\n"
            "    pass\n"
        )
        self.assertIn("@annotation.Executable()", code)

    def test_unimported_generated_decorator_stub_is_called(self):
        source = (
            "from micronaut.context.annotation import Executable\n"
            "@Singleton\n"
            "class Service:\n"
            "    pass\n"
        )
        transformer = MicronautTransformer(java_class_element, no_class_elements)
        transformer.visit(ast.parse(source))
        transformer.generated_decorators.add("Singleton")
        transformer.generated_decorator_code["jakarta.inject.Singleton"] = (
            "@micronaut_annotation('jakarta.inject.Singleton')\n"
            "def Singleton(*args, **kwargs):\n"
            "    def decorator(target):\n"
            "        return target\n"
            "    return decorator\n"
        )
        missing = transformer.get_missing_runtime_decorator_code(ast.parse(source))
        self.assertEqual(1, len(missing))
        runtime = MicronautRuntimeTransformer(java_class_element, no_class_elements, missing)
        self.assertIn("@Singleton()\nclass Service", unparse(runtime.visit(ast.parse(source))))

    def test_ordinary_decorators_are_untouched(self):
        source = (
            "import functools\n"
            "def plain(target):\n"
            "    return target\n"
            "@plain\n"
            "class Service:\n"
            "    @functools.lru_cache\n"
            "    def method(self):\n"
            "        pass\n"
        )
        self.assertTrue(ast_equal(ast.parse(source), MicronautRuntimeTransformer(java_class_element, no_class_elements).visit(ast.parse(source))))

    def test_custom_annotation_function_is_called_when_bare(self):
        code = runtime_source(
            "from micronaut.core.bind.annotation import Bindable\n"
            "@Bindable\n"
            "def Marker(value=''):\n"
            "    def decorator(target):\n"
            "        return target\n"
            "    return decorator\n"
            "@Marker\n"
            "class Bare:\n"
            "    pass\n"
            "@Marker('x')\n"
            "class Called:\n"
            "    pass\n"
        )
        self.assertIn("@Bindable()", code)
        self.assertIn("@Marker()\nclass Bare", code)
        self.assertIn("@Marker('x')\nclass Called", code)

    def test_direct_decorator_function_is_not_an_annotation_factory(self):
        source = (
            "from micronaut.aop import Around\n"
            "@Around\n"
            "def Timed(target):\n"
            "    return target\n"
            "@Timed\n"
            "class Service:\n"
            "    pass\n"
        )
        code = runtime_source(source)
        self.assertIn("@Around()", code)
        self.assertIn("@Timed\nclass Service", code)

    def test_imported_custom_annotation_function_is_called_when_bare(self):
        with tempfile.TemporaryDirectory() as source_root:
            package = os.path.join(source_root, "filters")
            os.makedirs(package)
            with open(os.path.join(package, "AdultMales.py"), "w", encoding="utf-8") as module:
                module.write(
                    "from micronaut.core.bind.annotation import Bindable\n"
                    "@Bindable\n"
                    "def AdultMales():\n"
                    "    def decorator(target):\n"
                    "        return target\n"
                    "    return decorator\n"
                )
            with open(os.path.join(package, "Adults.py"), "w", encoding="utf-8") as module:
                module.write(
                    "from .AdultMales import AdultMales\n"
                    "@AdultMales\n"
                    "def Adults(gender=''):\n"
                    "    def decorator(target):\n"
                    "        return target\n"
                    "    return decorator\n"
                )
            source = (
                "from .AdultMales import AdultMales\n"
                "from filters.Adults import Adults as Grown\n"
                "@AdultMales\n"
                "@Grown\n"
                "class View:\n"
                "    pass\n"
            )
            code = runtime_source(source, "filters", source_root)
            self.assertIn("@AdultMales()", code)
            self.assertIn("@Grown()", code)
            # Outside the source root the imports cannot be resolved and the module is left alone
            self.assertTrue(ast_equal(ast.parse(source), MicronautRuntimeTransformer(java_class_element, no_class_elements).visit(ast.parse(source))))

    def test_generated_decorator_is_a_factory(self):
        transformer = MicronautTransformer(java_class_element, no_class_elements)
        transformer.visit(ast.parse("from micronaut.context.annotation import Executable\n"))
        namespace = {}
        exec(transformer.get_generated_decorator_code()["io.micronaut.context.annotation.Executable"], namespace)

        class Target:
            pass

        class Value:
            pass

        executable = namespace["Executable"]
        self.assertIs(Target, executable()(Target))
        self.assertIs(Target, executable(Value)(Target))
        self.assertIs(Target, executable(value=Value)(Target))


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
