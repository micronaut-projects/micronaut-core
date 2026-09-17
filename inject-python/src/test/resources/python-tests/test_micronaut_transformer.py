"""
Unit tests for micronaut_transformer.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import os
import tempfile
import unittest
from unittest import mock

import java

from micronaut_transformer import MicronautTransformer, MicronautRuntimeTransformer, ast_equal, ensure_non_empty_bodies, unparse

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


class EnsureNonEmptyBodiesTest(unittest.TestCase):
    def test_emptied_blocks_receive_pass(self):
        tree = ast.parse("try:\n    x = 1\nexcept ImportError:\n    y = 2\nelse:\n    z = 3\n")
        tree.body[0].body.clear()
        tree.body[0].handlers[0].body.clear()
        ensure_non_empty_bodies(tree)
        self.assertEqual("try:\n    pass\nexcept ImportError:\n    pass\nelse:\n    z = 3", unparse(tree))
        compile(tree, "<test>", "exec")

    def test_emptied_final_body_receives_pass(self):
        tree = ast.parse("try:\n    x = 1\nfinally:\n    y = 2\n")
        tree.body[0].finalbody.clear()
        ensure_non_empty_bodies(tree)
        self.assertEqual("try:\n    x = 1\nfinally:\n    pass", unparse(tree))
        compile(tree, "<test>", "exec")

    def test_emptied_if_body_receives_pass(self):
        tree = ast.parse("if TYPE_CHECKING:\n    x = 1\nelse:\n    y = 2\n")
        tree.body[0].body.clear()
        ensure_non_empty_bodies(tree)
        self.assertEqual("if TYPE_CHECKING:\n    pass\nelse:\n    y = 2", unparse(tree))
        compile(tree, "<test>", "exec")

    def test_empty_module_and_populated_blocks_are_unchanged(self):
        source = "if True:\n    x = 1\n"
        tree = ast.parse(source)
        ensure_non_empty_bodies(tree)
        self.assertTrue(ast_equal(ast.parse(source), tree))
        empty = ast.parse("")
        ensure_non_empty_bodies(empty)
        self.assertEqual([], empty.body)

    def test_runtime_transformer_keeps_try_block_of_removed_build_call(self):
        source = "from pyronaut.build import Dependency\ntry:\n    Dependency('org:artifact:1.0')\nexcept Exception:\n    pass\n"
        transformed = MicronautRuntimeTransformer(no_class_element, no_class_elements).visit(ast.parse(source))
        ast.fix_missing_locations(transformed)
        self.assertIn("try:\n    pass\n", unparse(transformed))
        compile(transformed, "<test>", "exec")


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

    def test_wrapping_decorator_returning_a_nested_function_is_not_an_annotation_factory(self):
        # the most common Python decorator shape: it takes the target and returns a wrapper
        source = (
            "from micronaut.core.bind.annotation import Bindable\n"
            "@Bindable\n"
            "def Traced(func):\n"
            "    def wrapper(*args, **kwargs):\n"
            "        return 'traced:' + func(*args, **kwargs)\n"
            "    return wrapper\n"
            "class Service:\n"
            "    @Traced\n"
            "    def hello(self):\n"
            "        return 'ok'\n"
        )
        code = runtime_source(source)
        self.assertIn("@Bindable()", code)
        self.assertIn("    @Traced\n    def hello", code)

    def test_package_import_decorators_are_matched_by_their_qualifier(self):
        source = (
            "import micronaut.context.annotation as annotation\n"
            "def Executable(target):\n"
            "    return target\n"
            "@Executable\n"
            "class Local:\n"
            "    pass\n"
            "@annotation.Executable\n"
            "class Qualified:\n"
            "    pass\n"
            "@other.Executable\n"
            "class Other:\n"
            "    pass\n"
        )
        code = runtime_source(source)
        self.assertIn("@Executable\nclass Local", code)
        self.assertIn("@annotation.Executable()\nclass Qualified", code)
        self.assertIn("@other.Executable\nclass Other", code)

    def test_unaliased_package_import_decorators_are_matched_by_the_package_name(self):
        code = runtime_source(
            "import micronaut.context.annotation\n"
            "@micronaut.context.annotation.Executable\n"
            "class Service:\n"
            "    pass\n"
        )
        self.assertIn("@micronaut.context.annotation.Executable()", code)

    def test_imported_module_scan_is_not_cached_across_scans(self):
        with tempfile.TemporaryDirectory() as source_root:
            package = os.path.join(source_root, "filters")
            os.makedirs(package)
            annotations = os.path.join(package, "Annotations.py")
            factory = (
                "from micronaut.core.bind.annotation import Bindable\n"
                "@Bindable\n"
                "def Marker(value=''):\n"
                "    def decorator(target):\n"
                "        return target\n"
                "    return decorator\n"
            )
            plain = "def Marker(value=''):\n    return value\n"
            plain += "#" * (len(factory) - len(plain) - 1) + "\n"
            # a whole second: the file system may keep timestamps no finer than that
            written = 1_700_000_000 * 1_000_000_000
            with open(annotations, "w", encoding="utf-8") as module:
                module.write(plain)
            os.utime(annotations, ns=(written, written))
            source = (
                "from .Annotations import Marker\n"
                "@Marker\n"
                "class View:\n"
                "    pass\n"
            )
            self.assertIn("@Marker\nclass View", runtime_source(source, "filters", source_root))
            # the imported module changes to an annotation function of the same size and mtime (as an
            # incremental build within one compiler context may see it): a fresh scan reads it again
            with open(annotations, "w", encoding="utf-8") as module:
                module.write(factory)
            os.utime(annotations, ns=(written, written))
            self.assertEqual(len(plain), os.stat(annotations).st_size)
            self.assertIn("@Marker()\nclass View", runtime_source(source, "filters", source_root))

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
        transformer.visit(ast.parse("from micronaut.context.annotation import Executable, Replaces\n"))
        namespace = {}
        generated = transformer.get_generated_decorator_code()
        exec(generated["io.micronaut.context.annotation.Executable"], namespace)
        exec(generated["io.micronaut.context.annotation.Replaces"], namespace)

        class Target:
            pass

        class Value:
            pass

        executable = namespace["Executable"]
        replaces = namespace["Replaces"]
        self.assertIs(Target, executable()(Target))
        self.assertIs(Target, executable(processOnStartup=True)(Target))
        # a class is the value of a Class-typed value member
        self.assertIs(Target, replaces(Value)(Target))
        self.assertIs(Target, replaces(value=Value)(Target))

    def test_generated_decorator_reports_a_bare_application_it_could_not_see(self):
        # Bean = Singleton in another module, getattr(...): the factory receives the target itself
        transformer = MicronautTransformer(java_class_element, no_class_elements)
        transformer.visit(ast.parse("from micronaut.context.annotation import Executable\n"))
        namespace = {}
        exec(transformer.get_generated_decorator_code()["io.micronaut.context.annotation.Executable"], namespace)
        executable = namespace["Executable"]

        class Target:
            pass

        def method(self):
            pass

        with self.assertRaisesRegex(TypeError, "applied bare"):
            executable(Target)
        with self.assertRaisesRegex(TypeError, "applied bare"):
            executable(method)
        # the inner decorator of another generated factory is a nested annotation value, not a target
        self.assertIs(Target, executable(executable())(Target))

    def test_alias_of_a_generated_decorator_is_called_when_bare(self):
        code = runtime_source(
            "from micronaut.context.annotation import Executable\n"
            "Run = Executable\n"
            "class Service:\n"
            "    @Run\n"
            "    def method(self):\n"
            "        pass\n"
        )
        self.assertIn("@Run()", code)

    def test_custom_annotation_with_a_required_member_stays_an_annotation_function(self):
        source = (
            "from micronaut.core.bind.annotation import Bindable\n"
            "@Bindable\n"
            "def Tagged(value: str):\n"
            "    def decorator(target):\n"
            "        return target\n"
            "    return decorator\n"
            "@Tagged('x')\n"
            "def Composite():\n"
            "    def decorator(target):\n"
            "        return target\n"
            "    return decorator\n"
            "@Composite\n"
            "class Bean:\n"
            "    pass\n"
        )
        code = runtime_source(source)
        self.assertIn("@Tagged('x')\ndef Composite", code)
        self.assertIn("@Composite()\nclass Bean", code)

    def test_wrapping_decorator_with_only_varargs_is_applied_bare(self):
        source = (
            "from micronaut.core.bind.annotation import Bindable\n"
            "@Bindable\n"
            "def Star(*args):\n"
            "    def wrapper(*a, **k):\n"
            "        return args[0](*a, **k)\n"
            "    return wrapper\n"
            "class Service:\n"
            "    @Star\n"
            "    def method(self):\n"
            "        pass\n"
        )
        self.assertIn("    @Star\n    def method", runtime_source(source))

    def test_a_local_decorator_shadows_a_star_imported_annotation(self):
        source = (
            "from micronaut.context.annotation import *\n"
            "def Executable(target):\n"
            "    return target\n"
            "@Executable\n"
            "class Local:\n"
            "    pass\n"
        )
        self.assertIn("@Executable\nclass Local", runtime_source(source))

    def test_an_attribute_decorator_is_not_matched_on_its_attribute_name_alone(self):
        source = (
            "from micronaut.context.annotation import Executable\n"
            "@other.Executable\n"
            "class Service:\n"
            "    pass\n"
        )
        self.assertIn("@other.Executable\nclass Service", runtime_source(source))


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
        # the annotation function scanner hands the resolved element to the compiler, so a real annotation
        # type stands in for the swagger annotation, which is not on the class path of these tests
        header = java_class_element("io.micronaut.context.annotation.Primary")
        transformer = MicronautTransformer(lambda name: header, no_class_elements)
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


class UnresolvedJavaImportTest(unittest.TestCase):
    """An import from a Java package that names no class on the classpath is a compile error."""

    @staticmethod
    def _errors(source, class_elements=no_class_elements, python_source_dirs=None, class_element=no_class_element):
        transformer = MicronautTransformer(class_element, class_elements, python_source_dirs=python_source_dirs)
        transformer.visit(ast.parse(source))
        return transformer.validation_errors

    def test_standard_library_and_unknown_python_packages_are_not_java_imports(self):
        self.assertEqual([], self._errors("from typing import Annotated\nfrom dataclasses import dataclass\n"))
        self.assertEqual([], self._errors("from pydantic import BaseModel\n"))
        self.assertEqual([], self._errors("from .models import Pet\n"))

    def test_missing_name_in_a_reserved_java_namespace_is_an_error(self):
        errors = self._errors("from micronaut.absent.ua import UserAgentProvider\n")
        self.assertEqual(1, len(errors))
        self.assertIn("UserAgentProvider", errors[0])
        self.assertIn("micronaut.absent.ua", errors[0])
        self.assertIn("io.micronaut.absent.ua.UserAgentProvider", errors[0])
        self.assertEqual(1, len(self._errors("from io.lettuce.core.codec import RedisCodec\n")))
        self.assertEqual(1, len(self._errors("from jakarta.absent import Missing\n")))

    def test_missing_name_in_a_classpath_package_is_an_error(self):
        def acme_package(package):
            return [object()] if package == "com.acme" else []

        self.assertEqual(1, len(self._errors("from com.acme import Missing\n", acme_package)))
        self.assertEqual([], self._errors("from com.other import Missing\n", acme_package))

    def test_packages_and_java_type_modules_imported_as_names_are_not_errors(self):
        def inject_package(package):
            return [object()] if package == "jakarta.inject" else []

        def qualifier_type(name):
            return object() if name == "jakarta.inject.Qualifier" else None

        self.assertEqual([], self._errors("from jakarta import inject\n", inject_package))
        self.assertEqual([], self._errors("from jakarta.inject.Qualifier import Qualifier\n", class_element=qualifier_type))

    def test_project_python_modules_are_never_java_imports(self):
        import os
        import tempfile
        with tempfile.TemporaryDirectory() as source_dir:
            os.makedirs(os.path.join(source_dir, "micronaut", "docs"))
            with open(os.path.join(source_dir, "micronaut", "docs", "Product.py"), "w") as module:
                module.write("class Product:\n    pass\n")
            source = "from micronaut.docs.Product import Product\nfrom micronaut.docs import Product as Module\n"
            self.assertEqual([], self._errors(source, python_source_dirs=[source_dir]))
            self.assertEqual(2, len(self._errors(source)))


if __name__ == "__main__":
    unittest.main()
