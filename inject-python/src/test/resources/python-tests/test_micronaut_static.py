"""
Unit tests for micronaut_static.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest

from micronaut_processor import MicronautAstVisitor
from micronaut_static import CompileScope, StaticPlanner, MODE_ALL, MODE_ANNOTATED, MODE_OFF
from micronaut_typecheck import TypeChecker

NAMES = ["io.micronaut.context.python.annotation.CompileStatic", "pyronaut.build.CompileStatic"]


class Callback:
    def apply(self, item):
        return item


def plan(source, mode, strict=False, path="module.py"):
    """The decisions by qualified name and the planner, for a source modelled without a Java context."""
    checker = TypeChecker("off", [])
    visitor = MicronautAstVisitor(Callback(), "pkg", path, None, None, source_path=path, source_text=source, type_checker=checker)
    visitor.visit(ast.parse(source))
    planner = StaticPlanner(mode, NAMES, strict)
    decisions = planner.plan(checker)
    return {decision.qualifiedName(): decision for decision in decisions}, planner


def rules(decision):
    return [reason.rule() for reason in decision.reasons()]



CORPUS = '''
from abc import ABC, abstractmethod
from jakarta.inject import Singleton


@Singleton
class Finder:
    def named(self, flag: bool) -> str | None:
        return "x" if flag else None

    def counted(self, n: int) -> int | None:
        return n if n > 0 else None

    def listed(self) -> list:
        return self.mapped()

    def mapped(self) -> list[dict]:
        return [{"a": 1}]


class Mapper(ABC):
    @abstractmethod
    def to_thing(self, value: str) -> str:
        ...

    def described(self, value: str) -> str:
        """The docstring keeps the body a placeholder."""
        ...
'''


class CorpusFindingsTest(unittest.TestCase):
    """What running the compiler on real projects found: see the corpus closure plan."""

    def setUp(self):
        self.decisions, self.planner = plan(CORPUS, MODE_ALL)

    def test_an_abstract_method_is_not_a_candidate(self):
        for name in ("to_thing", "described"):
            decision = self.decisions[f"Mapper.{name}"]
            self.assertEqual("NOT_CANDIDATE", decision.outcome().name(), name)
            self.assertEqual(["abstract-method"], rules(decision), name)


SOURCE = '''
from micronaut.context.python.annotation import CompileStatic
from pyronaut.build import CompileStatic as BuildCompileStatic
from jakarta.inject import Singleton


@Singleton
class Plain:
    def __init__(self, rate: float):
        self.rate = rate

    def total(self, quantity: int, unit_price: float) -> float:
        return quantity * unit_price * self.rate

    @property
    def doubled(self) -> float:
        return self.rate * 2

    async def fetch(self, key: str) -> str:
        return key

    def items(self, count: int):
        for index in range(count):
            yield index

    def describe(self, *names: str, **options) -> str:
        return ",".join(names)

    def untyped(self, payload) -> str:
        return str(payload)

    def keyword_only(self, *, strict: bool) -> bool:
        return strict

    def unhinted_return(self, value: int):
        return value + 1

    def dynamic(self, values: list) -> int:
        with open("x") as handle:
            pass
        squares = [value * value for value in values]
        return len(squares)


@Singleton
@CompileStatic(False)
class Legacy:
    def run(self, value: int) -> int:
        return value

    @CompileStatic
    def fast(self, value: int) -> int:
        return value

    @CompileStatic
    def slow(self, *values: int) -> int:
        return len(values)


@BuildCompileStatic
class Aliased:
    def run(self, value: int) -> int:
        return value

    def __init__(self, value: int):
        self.value = value

    def loop(self, values: list) -> int:
        total = 0
        for value in values:
            total += value
        else:
            total = -1
        return total


def helper(value: int) -> int:
    return value
'''


class ScopeTest(unittest.TestCase):
    def test_the_nearest_switch_wins_and_the_mode_is_the_default(self):
        decisions, _ = plan(SOURCE, MODE_ALL)
        self.assertEqual("CANDIDATE", decisions["Plain.total"].outcome().name())
        self.assertEqual("MODE", decisions["Plain.total"].scope().name())
        self.assertEqual("EXCLUDED", decisions["Legacy.run"].outcome().name())
        self.assertEqual("CLASS", decisions["Legacy.run"].scope().name())
        self.assertEqual("CANDIDATE", decisions["Legacy.fast"].outcome().name())
        self.assertEqual("FUNCTION", decisions["Legacy.fast"].scope().name())
        self.assertEqual("CANDIDATE", decisions["Aliased.run"].outcome().name())
        self.assertEqual("CLASS", decisions["Aliased.run"].scope().name())

    def test_mode_annotated_compiles_the_switched_scopes_only(self):
        decisions, _ = plan(SOURCE, MODE_ANNOTATED)
        self.assertEqual("EXCLUDED", decisions["Plain.total"].outcome().name())
        self.assertEqual("MODE", decisions["Plain.total"].scope().name())
        self.assertEqual("CANDIDATE", decisions["Legacy.fast"].outcome().name())
        self.assertEqual("CANDIDATE", decisions["Aliased.run"].outcome().name())

    def test_mode_off_still_honours_an_explicit_switch(self):
        decisions, _ = plan(SOURCE, MODE_OFF)
        self.assertEqual("EXCLUDED", decisions["Plain.total"].outcome().name())
        self.assertEqual("CANDIDATE", decisions["Legacy.fast"].outcome().name())

    def test_a_module_level_switch_applies_to_the_whole_module(self):
        source = '''
from micronaut.context.python.annotation import CompileStatic
from jakarta.inject import Singleton

CompileStatic(False)

@Singleton
class Service:
    def run(self, value: int) -> int:
        return value

    @CompileStatic
    def fast(self, value: int) -> int:
        return value
'''
        decisions, _ = plan(source, MODE_ALL)
        self.assertEqual("EXCLUDED", decisions["Service.run"].outcome().name())
        self.assertEqual("MODULE", decisions["Service.run"].scope().name())
        self.assertEqual("CANDIDATE", decisions["Service.fast"].outcome().name())

    def test_unknown_modes_are_rejected(self):
        with self.assertRaises(ValueError):
            CompileScope("fast", NAMES)


class ClassificationTest(unittest.TestCase):
    def test_functions_that_can_never_be_compiled_are_not_candidates(self):
        decisions, _ = plan(SOURCE, MODE_ALL)
        self.assertEqual("NOT_CANDIDATE", decisions["Plain.__init__"].outcome().name())
        self.assertEqual(["special-method"], rules(decisions["Plain.__init__"]))
        self.assertNotIn("Plain.doubled", decisions)  # a property is not a function of the model
        self.assertEqual(["async-function"], rules(decisions["Plain.fetch"]))
        self.assertEqual(["generator-function"], rules(decisions["Plain.items"]))
        self.assertEqual("NOT_CANDIDATE", decisions["helper"].outcome().name())
        self.assertEqual(["class-not-eligible"], rules(decisions["helper"]))

    def test_signatures_without_a_java_layout_are_skipped_with_every_reason(self):
        decisions, _ = plan(SOURCE, MODE_ALL)
        self.assertEqual("SKIPPED", decisions["Plain.describe"].outcome().name())
        self.assertEqual(["varargs-signature", "varargs-signature"], rules(decisions["Plain.describe"]))
        self.assertEqual(["unhinted-parameter"], rules(decisions["Plain.untyped"]))
        self.assertEqual(["varargs-signature"], rules(decisions["Plain.keyword_only"]))
        # an unhinted return is the Object the stub declares: no longer a reason
        self.assertNotIn("unhinted-return", rules(decisions["Plain.unhinted_return"]))

    def test_unsupported_statements_and_expressions_are_each_a_reason(self):
        decisions, _ = plan(SOURCE, MODE_ALL)
        decision = decisions["Plain.dynamic"]
        self.assertEqual("SKIPPED", decision.outcome().name())
        self.assertEqual(["unsupported-statement", "unsupported-expression"], rules(decision))
        messages = [reason.message() for reason in decision.reasons()]
        self.assertEqual("a with statement has no static lowering", messages[0])
        self.assertEqual("a list comprehension has no static lowering", messages[1])
        self.assertEqual(39, decision.reasons()[0].span().line())
        self.assertEqual(["unsupported-statement"], rules(decisions["Aliased.loop"]))
        self.assertEqual("the else clause of a loop has no static lowering", decisions["Aliased.loop"].reasons()[0].message())

    def test_ineligible_classes_rule_out_their_functions(self):
        source = '''
from enum import Enum
from typing import Protocol
from micronaut.context.python.scope import ContextPooled

class Colour(Enum):
    RED = 1

    def label(self) -> str:
        return "red"

class Greeter(Protocol):
    def greet(self, name: str) -> str: ...

@ContextPooled
class Pooled:
    def run(self, value: int) -> int:
        return value

class Slotted:
    __slots__ = ("value",)

    def run(self, value: int) -> int:
        return value

class Dynamic:
    def __getattr__(self, name):
        return name

    def run(self, value: int) -> int:
        return value
'''
        decisions, _ = plan(source, MODE_ALL)
        for name in ("Colour.label", "Greeter.greet", "Pooled.run", "Slotted.run", "Dynamic.run"):
            self.assertEqual("NOT_CANDIDATE", decisions[name].outcome().name(), name)
            self.assertEqual(["class-not-eligible"], rules(decisions[name]), name)

    def test_the_statement_count_is_recorded(self):
        decisions, _ = plan(SOURCE, MODE_ALL)
        self.assertEqual(1, decisions["Plain.total"].stats().statements())
        self.assertEqual(3, decisions["Plain.dynamic"].stats().statements())


class WarningTest(unittest.TestCase):
    def test_an_explicit_switch_that_cannot_be_honoured_is_a_warning(self):
        _, planner = plan(SOURCE, MODE_ANNOTATED)
        messages = [diagnostic.message() for diagnostic in planner.diagnostics]
        self.assertEqual(2, len(messages), messages)
        self.assertTrue(messages[0].startswith("[Legacy.slow] cannot be compiled statically: [varargs-signature] parameter [*values]"), messages[0])
        self.assertTrue(messages[1].startswith("[Aliased.loop] cannot be compiled statically: [unsupported-statement]"), messages[1])
        self.assertTrue(all(diagnostic.kind().name() == "WARNING" for diagnostic in planner.diagnostics))
        # the constructor of the switched class is not a candidate, which is not worth a warning

    def test_strict_planning_reports_errors_and_mode_all_stays_quiet_for_classes(self):
        _, planner = plan(SOURCE, MODE_ANNOTATED, strict=True)
        self.assertTrue(all(diagnostic.kind().name() == "ERROR" for diagnostic in planner.diagnostics))
        _, planner = plan(SOURCE, MODE_ALL)
        self.assertEqual(["[Legacy.slow] cannot be compiled statically: [varargs-signature] parameter [*values] collects the positional arguments"],
                         [diagnostic.message() for diagnostic in planner.diagnostics])


if __name__ == "__main__":
    unittest.main()
