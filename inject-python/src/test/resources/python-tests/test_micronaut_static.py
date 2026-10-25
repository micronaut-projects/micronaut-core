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


class FakeAnnotation:
    """A Java annotation as the facts describe it, with no members."""

    def annotation(self):
        return True

    def validationConstraint(self):
        return False

    def interceptorBinding(self):
        return False


class FakeFacts:
    """
    The facts of a compilation without Java types: nothing is described but the annotation the
    processor adds to a dataclass, and a type fits itself.
    """

    def describe(self, name):
        return None

    def describeAnnotation(self, name):
        return FakeAnnotation() if name == "io.micronaut.core.annotation.Introspected" else None

    def isAssignable(self, source, target):
        return source == target


def plan(source, mode, strict=False, path="module.py", facts=None):
    """The decisions by qualified name and the planner, for a source modelled without a Java context."""
    checker = TypeChecker("off", [])
    visitor = MicronautAstVisitor(Callback(), "pkg", path, None, None, source_path=path, source_text=source, type_checker=checker)
    visitor.visit(ast.parse(source))
    if facts is not None:
        from micronaut_typecheck import PythonClasses
        checker.facts = facts
        checker.python_classes = PythonClasses(checker)
    planner = StaticPlanner(mode, NAMES, strict)
    decisions = planner.plan(checker)
    return {decision.qualifiedName(): decision for decision in decisions}, planner


def rules(decision):
    return [reason.rule() for reason in decision.reasons()]



CORPUS = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from jakarta.inject import Singleton


@dataclass
class Form:
    name: str = ""
    count: int = 0
    tags: list[str] = field(default_factory=list)


@Singleton
class Finder:
    form: Form | None = None

    def named(self, flag: bool) -> str | None:
        return "x" if flag else None

    def pick(self, fallback: Form) -> Form:
        return self.form or fallback

    def unhinted(self, flag: bool):
        return "x" if flag else "y"

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
        self.decisions, self.planner = plan(CORPUS, MODE_ALL, facts=FakeFacts())
        self.bodies = {body.methodName(): body for body in self.planner.bodies}

    def test_a_number_that_may_be_none_is_boxed(self):
        self.assertEqual("COMPILED", self.decisions["Finder.counted"].outcome().name(), rules(self.decisions["Finder.counted"]))
        self.assertEqual("java.lang.Integer", self.bodies["counted"].returnType())
        returned = list(self.bodies["counted"].body().statements())[0].value()
        self.assertEqual("Conditional", returned.getClass().getSimpleName())
        self.assertEqual("java.lang.Integer", returned.type())

    def test_an_abstract_method_is_not_a_candidate(self):
        for name in ("to_thing", "described"):
            decision = self.decisions[f"Mapper.{name}"]
            self.assertEqual("NOT_CANDIDATE", decision.outcome().name(), name)
            self.assertEqual(["abstract-method"], rules(decision), name)

    def _compiled(self, name):
        decision = self.decisions[f"Finder.{name}"]
        self.assertEqual("COMPILED", decision.outcome().name(), [(r.rule(), r.message()) for r in decision.reasons()])
        return self.bodies[name]

    def _returned(self, name):
        return list(self._compiled(name).body().statements())[-1].value()

    def test_or_on_an_attribute_of_an_object_yields_the_fallback_when_none(self):
        returned = self._returned("pick")
        self.assertEqual("Conditional", returned.getClass().getSimpleName())
        self.assertEqual("Compare", returned.test().getClass().getSimpleName())

    def test_an_unhinted_return_compiles_as_object(self):
        self.assertEqual("java.lang.Object", self._compiled("unhinted").returnType())


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

    def test_planning_again_reports_every_decision_and_warning_once(self):
        checker = TypeChecker("off", [])
        visitor = MicronautAstVisitor(Callback(), "pkg", "module.py", None, None, source_path="module.py", source_text=SOURCE, type_checker=checker)
        visitor.visit(ast.parse(SOURCE))
        planner = StaticPlanner(MODE_ANNOTATED, NAMES)
        first = [decision.qualifiedName() for decision in planner.plan(checker)]
        first_diagnostics = [diagnostic.message() for diagnostic in planner.diagnostics]
        # a second compilation element planned by the same planner
        second = [decision.qualifiedName() for decision in planner.plan(checker)]
        self.assertEqual(first, second)
        self.assertEqual(len(set(second)), len(second), second)
        self.assertEqual(first_diagnostics, [diagnostic.message() for diagnostic in planner.diagnostics])
        self.assertEqual(2, len(planner.diagnostics))

    def test_a_function_switch_on_a_property_accessor_is_not_a_candidate_and_is_reported(self):
        source = '''
from micronaut.context.python.annotation import CompileStatic
from jakarta.inject import Singleton


@Singleton
class Account:
    def __init__(self, balance: int):
        self._balance = balance

    @property
    @CompileStatic
    def balance(self) -> int:
        return self._balance

    @balance.setter
    @CompileStatic
    def balance(self, value: int) -> None:
        self._balance = value

    @balance.deleter
    @CompileStatic
    def balance(self) -> None:
        self._balance = 0

    @property
    def plain(self) -> int:
        return self._balance

    @property
    @CompileStatic(False)
    def legacy(self) -> int:
        return self._balance
'''
        for mode in (MODE_OFF, MODE_ANNOTATED, MODE_ALL):
            decisions, planner = plan(source, mode)
            for name in ("Account.balance", "Account.balance.setter", "Account.balance.deleter"):
                decision = decisions[name]
                self.assertEqual("NOT_CANDIDATE", decision.outcome().name(), name)
                self.assertEqual("FUNCTION", decision.scope().name(), name)
                self.assertEqual(["property-accessor"], rules(decision), name)
            # an accessor nothing switches on stays a property of the model, without a decision
            self.assertNotIn("Account.plain", decisions)
            self.assertNotIn("Account.legacy", decisions)
            messages = [diagnostic.message() for diagnostic in planner.diagnostics]
            self.assertEqual(["[Account.balance] cannot be compiled statically: [property-accessor] a property accessor is called through the Python object's attribute protocol",
                              "[Account.balance.setter] cannot be compiled statically: [property-accessor] a property accessor is called through the Python object's attribute protocol",
                              "[Account.balance.deleter] cannot be compiled statically: [property-accessor] a property accessor is called through the Python object's attribute protocol"],
                             messages, mode)
            self.assertTrue(all(diagnostic.kind().name() == "WARNING" for diagnostic in planner.diagnostics))
        _, planner = plan(source, MODE_ANNOTATED, strict=True)
        self.assertEqual(["ERROR", "ERROR", "ERROR"], [diagnostic.kind().name() for diagnostic in planner.diagnostics])

    def test_a_class_switch_on_a_class_that_can_never_be_compiled_is_flagged_once(self):
        source = '''
from enum import Enum
from micronaut.context.python.annotation import CompileStatic

@CompileStatic
class Colour(Enum):
    RED = 1

    def label(self) -> str:
        return "red"

    def code(self) -> int:
        return 1

@CompileStatic
class Slotted:
    __slots__ = ("value",)

    def run(self, value: int) -> int:
        return value

    def twice(self, value: int) -> int:
        return value * 2
'''
        _, planner = plan(source, MODE_ANNOTATED)
        messages = [diagnostic.message() for diagnostic in planner.diagnostics]
        self.assertEqual(["[Colour.label] cannot be compiled statically: [class-not-eligible] [Colour] is an enum",
                          "[Slotted.run] cannot be compiled statically: [class-not-eligible] [Slotted] declares __slots__"], messages)
        # planned again, the classes are flagged again, once each
        planner.plan(planner.checker)
        self.assertEqual(messages, [diagnostic.message() for diagnostic in planner.diagnostics])
        # mode all does not warn for a class-level switch
        _, planner = plan(source, MODE_ALL)
        self.assertEqual([], [diagnostic.message() for diagnostic in planner.diagnostics])


LOWERED = '''
from typing import Optional
from jakarta.inject import Singleton


@Singleton
class Pricing:
    count: int = 0

    def __init__(self, rate: float):
        self.rate = rate

    def total(self, quantity: int, unit_price: float) -> float:
        subtotal = quantity * unit_price
        if subtotal > 100:
            subtotal = subtotal - 5
        return subtotal

    def label(self, count: int, name: str) -> str:
        return f"{count} x {name}: {count * 2}"

    def ratio(self, a: int, b: int) -> float:
        return a / b

    def parity(self, n: int) -> str:
        return "even" if n % 2 == 0 else "odd"

    def truncate(self, n: int) -> int:
        remainder = n % 3
        return n // 3 + remainder

    def branches(self, flag: bool, n: int) -> int:
        if flag:
            result = n
        else:
            result = -n
        return result

    def with_rate(self, amount: float) -> float:
        return amount * self.rate

    def loops(self, n: int) -> int:
        total = 0
        while n > 0:
            total += n
            n -= 1
        return total

    def sibling(self, n: int) -> int:
        return self.truncate(n)

    def builtin(self, name: str) -> int:
        return len(name)

    def power(self, base: int, exponent: int) -> int:
        return base ** exponent

    def retyped(self, n: int) -> str:
        value = n
        value = "x"
        return value

    def falls_through(self, flag: bool) -> int:
        if flag:
            return 1

    def maybe_unbound(self, flag: bool) -> int:
        if flag:
            result = 1
        return result

    def guarded(self, n: int) -> int:
        assert n > 0, "positive"
        return n

    @staticmethod
    def helper(n: int) -> int:
        return n

    @property
    def doubled(self) -> float:
        return self.rate * 2

    def via_property(self) -> float:
        return self.doubled + 1

    def maybe(self, n: int) -> Optional[int]:
        return n

    def lazy_message(self, n: int, d: int) -> int:
        assert n > 0, f"{10 // d}"
        return n

    def counted(self) -> int:
        self.count += 1
        return self.count

    def rate_is_none(self) -> bool:
        return self.rate is None

    def n_is_none(self, n: int) -> bool:
        return n is None

    def mixed_local(self, flag: bool) -> str:
        value = 1.5
        if flag:
            value = 2
        return f"{value}"

    def hinted_float(self) -> float:
        value: float = 1
        return value

    def mixed_conditional(self, flag: bool) -> float:
        return 1 if flag else 2.5

    def float_power(self, a: float) -> float:
        return a ** 2

    def maybe_concat(self, s: str | None) -> str:
        return s + "x"

    def concat(self, s: str) -> str:
        return s + "x" + "y"

    def exact_compare(self, n: int, x: float) -> bool:
        return n > x

    def literal_compare(self, x: float) -> bool:
        return x > 100

    def several(self, name: str, n: int) -> int:
        while n > 0:
            n -= 1
        size = len(name)
        power = n ** n
        return size + power
'''


class LoweringTest(unittest.TestCase):
    def setUp(self):
        self.decisions, self.planner = plan(LOWERED, MODE_ALL, facts=FakeFacts())
        self.bodies = {body.methodName(): body for body in self.planner.bodies}

    def test_bodies_over_builtin_values_are_compiled(self):
        for name in ("total", "label", "ratio", "parity", "truncate", "branches", "with_rate"):
            self.assertEqual("COMPILED", self.decisions[f"Pricing.{name}"].outcome().name(), name)
            self.assertIn(name, self.bodies)
        total = self.bodies["total"]
        self.assertEqual(["quantity", "unit_price"], list(total.parameterNames()))
        self.assertEqual(["int", "double"], list(total.parameterTypes()))
        self.assertEqual("double", total.returnType())
        statements = list(total.body().statements())
        self.assertEqual(3, len(statements))
        local = statements[0]
        self.assertEqual("subtotal", local.name())
        self.assertEqual("double", local.type())
        product = local.value()
        self.assertEqual("*", product.op())
        self.assertEqual("double", product.type())
        # the int parameter is used as a long and widened to a double for the product
        self.assertEqual("long", product.left().operand().type())
        self.assertEqual("int", product.left().operand().parameterType())
        self.assertEqual(3, total.stats().statements())

    def test_integer_arithmetic_and_strings_lower_to_helpers(self):
        truncate = self.bodies["truncate"]
        self.assertEqual("int", truncate.returnType())
        statements = list(truncate.body().statements())
        self.assertEqual("%", statements[0].value().op())
        self.assertEqual("long", statements[0].value().type())
        returned = statements[1].value()
        self.assertEqual("int", returned.type())  # narrowed exactly to the declared int
        self.assertEqual("+", returned.operand().op())
        self.assertEqual("//", returned.operand().left().op())
        label = self.bodies["label"]
        parts = list(list(label.body().statements())[0].value().parts())
        self.assertEqual(5, len(parts))
        self.assertEqual("long", parts[0].type())
        self.assertEqual("java.lang.String", parts[1].type())
        self.assertEqual("*", parts[4].op())
        self.assertEqual("double", list(self.bodies["ratio"].body().statements())[0].value().type())
        parity = list(self.bodies["parity"].body().statements())[0].value()
        self.assertEqual("==", parity.test().op())
        self.assertEqual("java.lang.String", parity.type())

    def test_locals_assigned_in_branches_are_declared_once_at_the_top(self):
        statements = list(self.bodies["branches"].body().statements())
        self.assertEqual("result", statements[0].name())
        self.assertEqual("long", statements[0].type())
        branch = statements[1]
        self.assertEqual("result", list(branch.then().statements())[0].name())
        self.assertEqual("-", list(branch.orElse().statements())[0].value().op())

    def test_self_properties_are_read_through_the_stub(self):
        with_rate = self.bodies["with_rate"]
        product = list(with_rate.body().statements())[0].value()
        self.assertEqual("rate", product.right().property())
        self.assertEqual("double", product.right().type())
        self.assertEqual(1, with_rate.stats().bridgeCalls())

    def test_constructs_without_a_lowering_are_skipped_with_their_reason(self):
        expectations = {
            "loops": "unsupported-statement",
            "sibling": "sibling-call",
            "builtin": "python-builtin-not-lowered",
            "power": "unbounded-integer-op",
            "retyped": "unknown-type",
            "falls_through": "unknown-type",
            "maybe_unbound": "unsupported-statement",
            "via_property": "sibling-call",
            "maybe": "unsupported-expression",
            "mixed_local": "unknown-type",
            "hinted_float": "unknown-type",
            "mixed_conditional": "unsupported-expression",
            "float_power": "unsupported-expression",
            "maybe_concat": "unknown-type",
        }
        for name, rule in expectations.items():
            decision = self.decisions[f"Pricing.{name}"]
            self.assertEqual("SKIPPED", decision.outcome().name(), name)
            self.assertEqual(rule, decision.reasons()[0].rule(), f"{name}: {[(r.rule(), r.message()) for r in decision.reasons()]}")
            self.assertIsNotNone(decision.reasons()[0].span(), name)
        self.assertEqual("local [value] is a [long] and then a [java.lang.String]", self.decisions["Pricing.retyped"].reasons()[0].message())
        self.assertEqual("the function does not return a value on every path", self.decisions["Pricing.falls_through"].reasons()[0].message())

    def test_every_refused_statement_is_a_reason(self):
        # the lowering goes on after a refused statement; a read of a local whose assignment was
        # refused is not a reason of its own
        decision = self.decisions["Pricing.several"]
        self.assertEqual("SKIPPED", decision.outcome().name())
        self.assertEqual(["unsupported-statement", "python-builtin-not-lowered", "unbounded-integer-op"], rules(decision),
                         [(r.rule(), r.message()) for r in decision.reasons()])
        self.assertEqual([133, 135, 136], [reason.span().line() for reason in decision.reasons()])
        self.assertNotIn("several", self.bodies)

    def test_static_methods_are_not_candidates_yet(self):
        decision = self.decisions["Pricing.helper"]
        self.assertEqual("NOT_CANDIDATE", decision.outcome().name())
        self.assertEqual(["static-method"], rules(decision))

    def test_assertions_raise_through_the_helper(self):
        guarded = self.bodies["guarded"]
        statements = list(guarded.body().statements())
        self.assertEqual("If", statements[0].getClass().getSimpleName())
        self.assertEqual("not", statements[0].test().op())
        self.assertEqual(">", statements[0].test().operand().op())
        failure = list(statements[0].then().statements())[0].expression()
        self.assertEqual("assertion", failure.name())
        self.assertEqual(False, list(failure.arguments())[0].value())

    def test_the_assertion_message_is_evaluated_only_when_the_assertion_fails(self):
        statements = list(self.bodies["lazy_message"].body().statements())
        self.assertEqual("If", statements[0].getClass().getSimpleName())
        self.assertIsNone(statements[0].orElse())
        message = list(list(statements[0].then().statements())[0].expression().arguments())[1]
        self.assertEqual("StrJoin", message.getClass().getSimpleName())
        formatted = list(message.parts())[0]  # the f-string, spelled by str()
        self.assertEqual("//", list(formatted.parts())[0].op())

    def test_an_int_attribute_is_updated_in_place(self):
        statements = list(self.bodies["counted"].body().statements())
        self.assertEqual("PutSelf", statements[0].getClass().getSimpleName())
        self.assertEqual("int", statements[0].type())
        self.assertEqual("+", statements[0].value().operand().op())

    def test_a_none_test_keeps_the_value_it_tests(self):
        returned = list(self.bodies["rate_is_none"].body().statements())[0].value()
        self.assertEqual("Compare", returned.getClass().getSimpleName())
        self.assertEqual("is None", returned.op())
        self.assertEqual("java.lang.Double", returned.left().type())
        self.assertEqual("SelfProperty", returned.left().operand().getClass().getSimpleName())
        # a parameter of a number is never None, and reading it twice is reading it once
        returned = list(self.bodies["n_is_none"].body().statements())[0].value()
        self.assertEqual("Const", returned.getClass().getSimpleName())
        self.assertEqual(False, returned.value())

    def test_a_str_that_may_be_null_is_concatenated_through_the_helper(self):
        returned = list(self.bodies["concat"].body().statements())[0].value()
        self.assertEqual("Binary", returned.getClass().getSimpleName())
        self.assertEqual("concat", returned.op())
        self.assertEqual("Helper", returned.left().getClass().getSimpleName())
        self.assertEqual("concat", returned.left().name())
        self.assertEqual("Param", list(returned.left().arguments())[0].getClass().getSimpleName())
        self.assertIn("may be None", self.decisions["Pricing.maybe_concat"].reasons()[0].message())

    def test_an_int_and_a_float_compare_exactly(self):
        returned = list(self.bodies["exact_compare"].body().statements())[0].value()
        self.assertEqual(">", returned.op())
        self.assertEqual("compare", returned.left().name())
        self.assertEqual(0.0, returned.right().value())
        # a literal a double holds exactly compares as a double
        returned = list(self.bodies["literal_compare"].body().statements())[0].value()
        self.assertEqual(">", returned.op())
        self.assertEqual("Cast", returned.right().getClass().getSimpleName())


OBJECTS = '''
from dataclasses import dataclass


class Plain:
    pass


class Sized:
    def __len__(self) -> int:
        return 0


class Falsy:
    def __bool__(self) -> bool:
        return False


class Derived(Falsy):
    pass


class Widened:
    pass


class Narrowed(Widened):
    __len__ = None


class Spelled:
    def __str__(self) -> str:
        return "spelled"


class Represented:
    def __repr__(self) -> str:
        return "represented"


class Inherited(Represented):
    pass


class Named:
    def __str__(self) -> str:
        return "named"


class Renamed(Named):
    def toString(self) -> str:
        return "renamed"


@dataclass
class Form:
    name: str = ""


class Checks:
    def plain(self, value: Plain) -> bool:
        return not value

    def sized(self, value: Sized) -> bool:
        return not value

    def falsy(self, value: Falsy) -> bool:
        return not value

    def derived(self, value: Derived) -> bool:
        return not value

    def widened(self, value: Widened) -> bool:
        return not value

    def spelled(self, value: Spelled) -> str:
        return str(value)

    def represented(self, value: Represented) -> str:
        return f"{value}"

    def inherited(self, value: Inherited) -> str:
        return str(value)

    def renamed(self, value: Named) -> str:
        return str(value)

    def form(self, value: Form) -> str:
        return f"{value}"

    def unspelled(self, value: Plain) -> str:
        return str(value)
'''


class ObjectsOfTheCompilationTest(unittest.TestCase):
    """Truthiness and str() of an object of a class of the compilation follow what Python resolves."""

    def setUp(self):
        self.decisions, _ = plan(OBJECTS, MODE_ALL, facts=FakeFacts())

    def _decided(self, name):
        decision = self.decisions[f"Checks.{name}"]
        return decision.outcome().name(), rules(decision)

    def test_an_object_without_bool_or_len_is_true_unless_none(self):
        self.assertEqual(("COMPILED", []), self._decided("plain"))

    def test_truthiness_from_bool_or_len_is_refused(self):
        # __len__ and __bool__ of the class, of a base, or of a subclass a value of the class may be
        for name in ("sized", "falsy", "derived", "widened"):
            self.assertEqual(("SKIPPED", ["truthiness-of-java-object"]), self._decided(name), name)

    def test_str_is_compiled_where_the_stub_bridges_to_string(self):
        for name in ("spelled", "represented", "inherited"):
            self.assertEqual(("COMPILED", []), self._decided(name), name)

    def test_str_of_a_class_without_a_source_str_is_refused(self):
        # a dataclass's __repr__ is generated, not in its source; a Python toString takes precedence in the stub
        for name in ("form", "unspelled", "renamed"):
            self.assertEqual(("SKIPPED", ["unsupported-expression"]), self._decided(name), name)


if __name__ == "__main__":
    unittest.main()
