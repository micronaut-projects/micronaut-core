"""
Unit tests for micronaut_typecheck.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest

from micronaut_processor import MicronautAstVisitor
from micronaut_typecheck import TypeChecker, TypeCheckScope, MODE_ERROR, MODE_OFF, MODE_WARN

NAMES = ["io.micronaut.context.python.annotation.TypeChecked", "pyronaut.build.TypeChecked"]


class Callback:
    def apply(self, item):
        return item


def units(source, mode, path="module.py"):
    checker = TypeChecker(mode, NAMES)
    visitor = MicronautAstVisitor(Callback(), "pkg", path, None, None, source_path=path, source_text=source, type_checker=checker)
    visitor.visit(ast.parse(source))
    return {unit.qualified_name: unit.severity for unit in checker.units()}


SOURCE = '''
from micronaut.context.python.annotation import TypeChecked
from pyronaut.build import TypeChecked as BuildTypeChecked


class Plain:
    def run(self) -> str:
        return "x"


@TypeChecked(False)
class Skipped:
    def run(self) -> str:
        return "x"

    @TypeChecked
    def still_checked(self) -> str:
        return "x"


@BuildTypeChecked
class Aliased:
    def __init__(self, value: str):
        self.value = value

    def run(self) -> str:
        return "x"

    @TypeChecked(False)
    def opted_out(self) -> str:
        return "x"


def helper() -> str:
    return "x"
'''


class ScopeResolutionTest(unittest.TestCase):
    def test_the_mode_is_the_default_and_the_nearest_switch_wins(self):
        severities = units(SOURCE, MODE_WARN)
        self.assertEqual(MODE_WARN, severities["Plain.run"])
        self.assertIsNone(severities["Skipped.run"])
        self.assertEqual(MODE_WARN, severities["Skipped.still_checked"])
        self.assertEqual(MODE_WARN, severities["Aliased.run"])
        self.assertEqual(MODE_WARN, severities["Aliased.__init__"])
        self.assertIsNone(severities["Aliased.opted_out"])
        self.assertEqual(MODE_WARN, severities["helper"])

    def test_opting_in_under_mode_off_checks_at_error_severity(self):
        severities = units(SOURCE, MODE_OFF)
        self.assertIsNone(severities["Plain.run"])
        self.assertIsNone(severities["Skipped.run"])
        self.assertEqual(MODE_ERROR, severities["Skipped.still_checked"])
        self.assertEqual(MODE_ERROR, severities["Aliased.run"])
        self.assertIsNone(severities["Aliased.opted_out"])
        self.assertIsNone(severities["helper"])

    def test_a_module_level_switch_applies_to_the_whole_module(self):
        source = '''
from micronaut.context.python.annotation import TypeChecked
from jakarta.inject import Singleton

TypeChecked(False)

@Singleton
class Service:
    def run(self) -> str:
        return "x"

    @TypeChecked
    def checked(self) -> str:
        return "x"
'''
        severities = units(source, MODE_ERROR)
        self.assertIsNone(severities["Service.run"])
        self.assertEqual(MODE_ERROR, severities["Service.checked"])

    def test_unknown_modes_are_rejected(self):
        with self.assertRaises(ValueError):
            TypeCheckScope("strict", NAMES)

    def test_string_and_boolean_switch_values(self):
        class Decorator:
            def __init__(self, value):
                self._value = value

            def annotationName(self):
                return NAMES[0]

            def members(self):
                return {} if self._value is None else {"value": self._value}

        scope = TypeCheckScope(MODE_WARN, NAMES)
        self.assertTrue(scope.switch([Decorator(None)]))
        self.assertTrue(scope.switch([Decorator(True)]))
        self.assertFalse(scope.switch([Decorator(False)]))
        self.assertFalse(scope.switch([Decorator("False")]))
        self.assertIsNone(scope.switch([]))


class SuggestionTest(unittest.TestCase):
    def test_suggestions_prefer_case_then_camel_case_then_close_spellings(self):
        from micronaut_typecheck import suggest, literal_kind, literal_fits
        members = ["value", "consumes", "produces", "uri", "uris"]
        self.assertEqual(["consumes"], suggest("Consumes", members))
        self.assertEqual(["consumes"], suggest("consume", members))
        # a short name allows one edit only, so "uris" is not offered for "url"
        self.assertEqual(["uri"], suggest("url", members))
        self.assertEqual(["produces"], suggest("producer", members))
        self.assertEqual([], suggest("something", members))
        self.assertEqual(["produceValue"], suggest("produce_value", ["produceValue", "other"]))
        self.assertEqual("bool", literal_kind(ast.parse("True").body[0].value))
        self.assertEqual("int", literal_kind(ast.parse("1").body[0].value))
        self.assertIsNone(literal_kind(ast.parse("NAME").body[0].value))

        class Member:
            def __init__(self, java_type, enum=False):
                self._type = java_type
                self._enum = enum

            def type(self):
                return self._type

            def enumType(self):
                return self._enum

        self.assertTrue(literal_fits("str", Member("java.lang.String")))
        self.assertFalse(literal_fits("int", Member("java.lang.String")))
        self.assertTrue(literal_fits("int", Member("double")))
        self.assertFalse(literal_fits("str", Member("boolean")))
        self.assertTrue(literal_fits("str", Member("io.micronaut.http.HttpMethod", enum=True)))
        self.assertTrue(literal_fits("int", Member("java.lang.Object")))
