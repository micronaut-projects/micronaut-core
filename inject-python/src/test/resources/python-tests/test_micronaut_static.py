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


class FakeFacts:
    """The facts of a compilation without Java types: nothing is described, and a type fits itself."""

    def describe(self, name):
        return None

    def describeAnnotation(self, name):
        return None

    def isAssignable(self, source, target):
        supertypes = {
            "java.util.List": {"java.util.Collection", "java.lang.Iterable"},
            "java.util.Set": {"java.util.Collection", "java.lang.Iterable"},
            "java.util.Collection": {"java.lang.Iterable"},
        }
        return source == target or target in supertypes.get(source, ())


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


def _uncast(expression):
    """The expression under its casts."""
    while expression.getClass().getSimpleName() == "Cast":
        expression = expression.operand()
    return expression


def rules(decision):
    return [reason.rule() for reason in decision.reasons()]



CORPUS = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from jakarta.inject import Singleton

ROLE = "user"
LIMIT = 20


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

    def role(self) -> str:
        return ROLE

    def limit(self, n: int) -> int:
        return n + LIMIT

    def form_of(self) -> Form:
        return Form(name="x")

    def form_count(self, form: Form) -> int:
        return Form(form.name, count=3).count

    def rename(self, form: Form, name: str) -> None:
        form.name = name

    def unhinted(self, flag: bool):
        return "x" if flag else "y"

    def uses(self, flag: bool) -> str:
        return self.unhinted(flag)

    def maybe(self, flag: bool) -> str:
        if flag:
            token = "t"
            return token
        return "n"

    def merged(self, extra: dict[str, str]) -> dict[str, str]:
        return {"a": "b", **extra, "c": "d"}

    def view(self, name: str, count: int):
        return {"name": name, "count": count}

    def copied(self, values: list[str]) -> list[str]:
        return list(values)

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

    def test_a_union_with_none_returns_its_member(self):
        self.assertEqual("COMPILED", self.decisions["Finder.named"].outcome().name(), rules(self.decisions["Finder.named"]))
        self.assertEqual("java.lang.String", self.bodies["named"].returnType())

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

    def test_a_module_constant_is_inlined(self):
        returned = self._returned("role")
        self.assertEqual("Const", returned.getClass().getSimpleName())
        self.assertEqual("user", returned.value())
        returned = _uncast(self._returned("limit"))
        self.assertEqual("Binary", returned.getClass().getSimpleName())
        self.assertEqual(20, returned.right().value())

    def test_a_construction_by_keyword_fills_the_defaults(self):
        returned = self._returned("form_of")
        self.assertEqual("NewJava", returned.getClass().getSimpleName())
        self.assertEqual(["java.lang.String", "int", "java.util.List<java.lang.String>"], list(returned.parameterTypes()))
        arguments = list(returned.arguments())
        self.assertEqual("x", arguments[0].value())
        self.assertEqual(0, _uncast(arguments[1]).value())
        self.assertEqual("Helper", arguments[2].getClass().getSimpleName())
        self.assertEqual("list", arguments[2].name())
        construction = _uncast(self._returned("form_count")).receiver()
        self.assertEqual("NewJava", construction.getClass().getSimpleName())
        self.assertEqual(3, _uncast(list(construction.arguments())[1]).value())

    def test_an_attribute_of_an_object_of_the_compilation_is_assigned_through_its_setter(self):
        statement = list(self._compiled("rename").body().statements())[0]
        self.assertEqual("Eval", statement.getClass().getSimpleName())
        call = statement.expression()
        self.assertEqual("InvokeJava", call.getClass().getSimpleName())
        self.assertEqual("setName", call.name())
        self.assertEqual(["java.lang.String"], list(call.parameterTypes()))

    def test_or_on_an_attribute_of_an_object_yields_the_fallback_when_none(self):
        returned = self._returned("pick")
        self.assertEqual("Conditional", returned.getClass().getSimpleName())
        self.assertEqual("Compare", returned.test().getClass().getSimpleName())

    def test_an_unhinted_return_compiles_as_object(self):
        self.assertEqual("java.lang.Object", self._compiled("unhinted").returnType())

    def test_a_collection_of_another_element_type_is_cast_through_the_raw_type(self):
        # listed() -> list returns mapped() -> list[dict]: the sibling's List<Map<Object,Object>> reaches the raw List through a cast
        self.assertEqual("COMPILED", self.decisions["Finder.listed"].outcome().name(), rules(self.decisions["Finder.listed"]))
        self.assertEqual("java.util.List<java.lang.Object>", self.bodies["listed"].returnType())
        returned = list(self.bodies["listed"].body().statements())[0].value()
        self.assertEqual("Cast", returned.getClass().getSimpleName())
        self.assertEqual("java.util.List<java.lang.Object>", returned.type())
        self.assertEqual("java.util.List<java.util.Map<java.lang.Object,java.lang.Object>>", returned.operand().type())

    def test_a_caller_of_an_unhinted_sibling_learns_the_type_its_body_returns(self):
        returned = self._returned("uses")
        self.assertEqual("Cast", returned.getClass().getSimpleName())
        self.assertEqual("java.lang.String", returned.type())
        self.assertEqual("java.lang.Object", returned.operand().type())

    def test_a_dict_unpacking_builds_the_map_in_order(self):
        returned = self._returned("merged")
        self.assertEqual("Helper", returned.getClass().getSimpleName())
        self.assertEqual("put", returned.name())
        self.assertEqual("putAll", list(returned.arguments())[0].name())
        self.assertEqual("map", list(list(returned.arguments())[0].arguments())[0].name())
        self.assertEqual("java.util.Map<java.lang.String,java.lang.String>", returned.type())

    def test_a_literal_of_mixed_values_holds_objects(self):
        body = self._compiled("view")
        self.assertEqual("java.lang.Object", body.returnType())
        returned = self._returned("view")
        self.assertEqual("java.util.Map<java.lang.String,java.lang.Object>", returned.type())

    def test_list_of_a_collection_copies_it(self):
        returned = self._returned("copied")
        self.assertEqual("Helper", returned.getClass().getSimpleName())
        self.assertEqual("copyOfList", returned.name())
        self.assertEqual("java.util.List<java.lang.String>", returned.type())

    def test_a_local_read_inside_its_branch_only_is_declared_there(self):
        body = self._compiled("maybe")
        statements = list(body.body().statements())
        self.assertEqual("If", statements[0].getClass().getSimpleName())
        self.assertEqual("Local", list(statements[0].then().statements())[0].getClass().getSimpleName())


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

    def new(self) -> str:
        return "new"

    def priced(self, default: int) -> int:
        return default


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
        self.assertEqual(["java-reserved-name"], rules(decisions["Plain.new"]))
        self.assertEqual(["java-reserved-name"], rules(decisions["Plain.priced"]))

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


LOWERED = '''
from typing import Annotated, Optional, Protocol
from jakarta.inject import Inject, Singleton
from java.lang import Exception, RuntimeException
from micronaut.context.annotation import Executable


class Shelf(Protocol):
    def size(self) -> int:
        ...


class Cart:
    items: int = 0

    def __init__(self, items: int, owner: str):
        self.items = items
        self.owner = owner

    @staticmethod
    def tag(n: int) -> int:
        return n

    def total(self, n: int) -> int:
        return self.items * n

    def _hidden(self) -> int:
        return 1


@Singleton
class Pricing:
    count: int = 0

    def __init__(self, rate: float):
        self.rate = rate

    def carted(self, cart: Cart, n: int) -> int:
        return cart.total(n) + cart.items + cart._hidden()

    def built(self, n: int) -> str:
        return Cart(n, "x").owner

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

    def hidden_call(self, n: int) -> int:
        return self._secret(n) + self.defaulted()

    def _secret(self, n: int) -> int:
        return n

    def defaulted(self, n: int = 1) -> int:
        return n

    def builtin(self, name: str) -> int:
        return round(len(name) / 2)

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

    def switched(self, n: int) -> int:
        switch = n
        return switch

    def summed(self, n: int) -> int:
        total = 0
        for i in range(1, n + 1, 2):
            if i == 7:
                break
            if i == 3:
                continue
            total += i
        return total

    def countdown(self, n: int) -> str:
        parts = ""
        while n > 0:
            parts = parts + str(n)
            n = n - 1
        return parts

    def leaked(self, n: int) -> int:
        for i in range(n):
            last = i
        return last

    def guarded_by_java(self, n: int) -> int:
        try:
            return n
        finally:
            n = 0

    def raises_python(self, n: int) -> int:
        raise ValueError("no")

    def shadowed(self, n: int) -> int:
        try:
            return n
        except Exception as broad:
            return 1
        except RuntimeException as narrow:
            return 2

    def keyed(self, prices: dict[str, int]) -> int:
        total = 0
        for name in prices:
            total += 1
        return total

    def tried(self, n: int) -> int:
        try:
            result = n + 1
        finally:
            pass
        return result

    def relooped(self, n: int) -> int:
        for i in range(n):
            pass
        for i in range(n):
            pass
        i = 2
        return i

    def collected(self, names: list[str], limit: int) -> list[str]:
        picked: list[str] = []
        for name in names:
            if len(picked) >= limit:
                break
            if name in picked or not name.strip():
                continue
            picked.append(name.upper())
        return picked

    def indexed(self, values: list[int], index: int) -> int:
        first = values[0]
        return first + values[index] + len(values)

    def priced(self, prices: dict[str, float], name: str) -> float:
        if name in prices:
            return prices[name]
        return prices.get("default", 0.0)

    def parsed(self, text: str) -> int:
        parts = text.split(",")
        total = 0
        for part in parts:
            total += int(part)
        return max(total, 0)

    def mixed(self) -> list[int]:
        return [1, "x"]

    def maybe_missing(self, values: dict[str, int]) -> bool:
        return values.get("x") is None

    def appended(self, values: list[int]) -> bool:
        return values.append(1) is None

    def crossed(self, values: list[int]) -> bool:
        return 1.0 in values

    def charred(self, text: str) -> bool:
        return 1 in text

    def counted(self, counts: dict[str, int], values: list[str]) -> int:
        counts["added"] = len(values)
        return counts["added"]
    def lazy_message(self, n: int, d: int) -> int:
        assert n > 0, f"{10 // d}"
        return n

    def bumped(self) -> int:
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

    def tagged(self, cart: Cart, n: int) -> int:
        return cart.tag(n)

    def shelved(self, shelf: Shelf) -> int:
        return shelf.size()

    def shelf_made(self) -> int:
        return Shelf().size()

    def tagged_afresh(self, n: int) -> int:
        return Cart(n, "x").tag(n)

    def paged(self, pages: dict[str, int]) -> int:
        total = 0
        for title, count in pages.items():
            if len(title) > 0:
                total = total + count
        return total


pricing: Annotated[Pricing, Inject]


@Executable
def route(n: int) -> int:
    return pricing.truncate(n) + n


@Executable
def peek(cart: Cart) -> str:
    return cart.owner


def helper_only(n: int) -> int:
    return n
'''


ADVISED = '''
from jakarta.inject import Singleton


def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator


@micronaut_annotation("pkg.Logged")
def Logged():
    def decorator(target):
        return target
    return decorator


@Singleton
class Audit:
    @Logged
    def label(self, n: int) -> str:
        return str(n)

    def plain(self, n: int) -> int:
        return n
'''


class AroundBinding:
    """What the planner asks of the description of an around binding."""

    def annotation(self):
        return True

    def interceptorBinding(self):
        return True

    def validationConstraint(self):
        return False

    def executable(self):
        return True

    def introduction(self):
        return False


class AdvisedFacts(FakeFacts):
    """Facts describing the decorator Logged as an around binding."""

    def describeAnnotation(self, name):
        return AroundBinding() if name.rsplit(".", 1)[-1] == "Logged" else None


MIXED = '''
from jakarta.inject import Singleton
import java

Describable = java.type("io.micronaut.python.annotation.processing.test.defaults.Describable")


class Base(Describable):
    def name(self) -> str:
        return "base"


@Singleton
class Sub(Base, Describable):
    def name(self) -> str:
        return "sub"


@Singleton
class Consumer:
    def __init__(self, target: Sub):
        self.target = target

    def describe_target(self) -> str:
        return self.target.describe()

    def name_target(self) -> str:
        return self.target.name()
'''


class MixedBasesTest(unittest.TestCase):
    def test_a_java_base_among_the_bases_of_a_receiver(self):
        decisions, planner = plan(MIXED, MODE_ALL, facts=FakeFacts())
        bodies = {body.methodName(): body for body in planner.bodies}
        self.assertEqual("COMPILED", decisions["Consumer.name_target"].outcome().name(), [(r.rule(), r.message()) for r in decisions["Consumer.name_target"].reasons()])
        call = _uncast(list(bodies["name_target"].body().statements())[0].value())
        self.assertEqual("InvokeJava", call.getClass().getSimpleName())
        # a default method of the Java interface is not modelled: the call is refused, not crashed on
        self.assertEqual("SKIPPED", decisions["Consumer.describe_target"].outcome().name())
        self.assertEqual(["unknown-type"], rules(decisions["Consumer.describe_target"]))


class AdviceTest(unittest.TestCase):
    def test_an_advised_method_is_compiled_with_its_chain(self):
        decisions, planner = plan(ADVISED, MODE_ALL, facts=AdvisedFacts())
        self.assertEqual("COMPILED", decisions["Audit.label"].outcome().name(), [(r.rule(), r.message()) for r in decisions["Audit.label"].reasons()])
        bodies = {body.methodName(): body for body in planner.bodies}
        self.assertTrue(bodies["label"].advised())
        self.assertFalse(bodies["plain"].advised())



class Description:
    """The description of an annotation, with the flags the planner reads."""

    def __init__(self, scope=False, executable=False, interceptor=False, constraint=False):
        self._scope, self._executable, self._interceptor, self._constraint = scope, executable, interceptor, constraint

    def annotation(self):
        return True

    def interceptorBinding(self):
        return self._interceptor

    def validationConstraint(self):
        return self._constraint

    def executable(self):
        return self._executable

    def introduction(self):
        return False

    def scope(self):
        return self._scope


class ScriptFacts(FakeFacts):
    """Facts describing the annotations of a module of routes as Micronaut declares them."""

    DESCRIPTIONS = {
        "Singleton": Description(scope=True, executable=True),
        "ContextPooled": Description(scope=True, executable=True),
        "Controller": Description(executable=True),
        "Requires": Description(),
        "Get": Description(executable=True),
        "NotBlank": Description(constraint=True),
        "Logged": Description(executable=True, interceptor=True),
    }

    def describeAnnotation(self, name):
        return self.DESCRIPTIONS.get(name.rsplit(".", 1)[-1])


SCRIPTED = '''
from typing import Annotated
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
from micronaut.context.annotation import Requires
from micronaut.context.python.scope import ContextPooled
from micronaut.http.annotation import Controller, Get


def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator


@micronaut_annotation("pkg.Logged")
def Logged():
    def decorator(target):
        return target
    return decorator


{module}


class Cart:
    def __init__(self, owner: str):
        self.owner = owner


@Get("/peek")
def peek(cart: Cart) -> str:
    return cart.owner


@Get("/plain/{{n}}")
def plain(n: int) -> int:
    return n + 1


@Get("/checked/{{name}}")
def checked(name: Annotated[str, NotBlank]) -> str:
    return name


@Get("/logged/{{n}}")
@Logged
def logged(n: int) -> int:
    return n
'''


class ScriptTest(unittest.TestCase):
    def test_a_module_level_annotation_that_declares_no_scope_keeps_the_module_pooled(self):
        # PythonScriptElement pools every bean module that declares no scope: @Controller or @Requires do not
        for module in ("", 'Controller("/c")', 'Requires(property="x")', 'Controller("/c")\nContextPooled()'):
            decisions, _ = plan(SCRIPTED.format(module=module), MODE_ALL, facts=ScriptFacts())
            self.assertEqual("SKIPPED", decisions["peek"].outcome().name(), module)
            self.assertEqual(["pooled-module"], rules(decisions["peek"]), module)
            self.assertEqual("COMPILED", decisions["plain"].outcome().name(), module)

    def test_a_module_declaring_a_scope_reaches_python_objects(self):
        decisions, _ = plan(SCRIPTED.format(module='Controller("/c")\nSingleton()'), MODE_ALL, facts=ScriptFacts())
        self.assertEqual("COMPILED", decisions["peek"].outcome().name(), [(r.rule(), r.message()) for r in decisions["peek"].reasons()])

    def test_an_advised_module_function_is_not_compiled(self):
        # the generated class of a module runs no compiled advice: validation and around bindings would be bypassed
        for module in ("", 'Controller("/c")\nSingleton()'):
            decisions, _ = plan(SCRIPTED.format(module=module), MODE_ALL, facts=ScriptFacts())
            for name, advice in (("checked", "NotBlank"), ("logged", "Logged")):
                self.assertEqual("NOT_CANDIDATE", decisions[name].outcome().name(), name)
                self.assertEqual(["intercepted-method"], rules(decisions[name]), name)
                self.assertIn(f"[{advice}]", decisions[name].reasons()[0].message())

    def test_a_module_level_around_binding_advises_every_function(self):
        decisions, _ = plan(SCRIPTED.format(module='Logged()'), MODE_ALL, facts=ScriptFacts())
        self.assertEqual("NOT_CANDIDATE", decisions["plain"].outcome().name())
        self.assertEqual(["intercepted-method"], rules(decisions["plain"]))

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

    def test_a_protocol_is_called_through_its_interface_and_never_constructed(self):
        shelved = _uncast(list(self.bodies["shelved"].body().statements())[0].value())
        self.assertEqual("InvokeJava", shelved.getClass().getSimpleName())
        self.assertTrue(shelved.owner().endswith("Shelf"), shelved.owner())
        self.assertEqual("SKIPPED", self.decisions["Pricing.shelf_made"].outcome().name())
        self.assertEqual(["unsupported-expression"], rules(self.decisions["Pricing.shelf_made"]))

    def test_a_static_method_is_called_on_a_plain_receiver_only(self):
        tagged = _uncast(list(self.bodies["tagged"].body().statements())[0].value())
        self.assertEqual("InvokeJava", tagged.getClass().getSimpleName())
        self.assertIsNone(tagged.receiver())
        # Python constructs the Cart before calling the static method; a static Java call would not
        self.assertEqual("SKIPPED", self.decisions["Pricing.tagged_afresh"].outcome().name())
        self.assertEqual(["unsupported-expression"], rules(self.decisions["Pricing.tagged_afresh"]))

    def test_dict_entries_unpack_into_the_loop_variables(self):
        paged = self.bodies["paged"]
        loop = list(paged.body().statements())[2]  # after the copy of the parameter and total = 0
        self.assertEqual("ForEach", loop.getClass().getSimpleName())
        self.assertEqual("java.util.Map.Entry", loop.type())
        self.assertEqual("entrySet", loop.iterable().name())
        bindings = list(loop.body().statements())[:2]
        self.assertEqual(["title", "count"], [binding.name() for binding in bindings])
        self.assertEqual(["java.lang.String", "long"], [binding.type() for binding in bindings])
        self.assertEqual(["getKey", "getValue"], [_uncast(binding.value()).name() for binding in bindings])
        self.assertEqual("COMPILED", self.decisions["Pricing.paged"].outcome().name())

    def test_module_functions_compile_into_the_script_class(self):
        route = self.decisions["route"]
        self.assertEqual("COMPILED", route.outcome().name(), [(r.rule(), r.message()) for r in route.reasons()])
        body = self.bodies["route"]
        self.assertTrue(body.className().endswith("Module"), body.className())  # the generated class of module.py
        call = _uncast(_uncast(list(body.body().statements())[0].value()).left())
        self.assertEqual("truncate", call.name())
        self.assertEqual("ModuleAttribute", call.receiver().getClass().getSimpleName())
        self.assertEqual("pricing", call.receiver().name())
        self.assertEqual(call.receiver().owner(), body.className())
        # a module without a module-level annotation is served by a context pool: a Python read is out of reach
        self.assertEqual("SKIPPED", self.decisions["peek"].outcome().name())
        self.assertEqual(["pooled-module"], rules(self.decisions["peek"]))
        self.assertEqual("NOT_CANDIDATE", self.decisions["helper_only"].outcome().name())
        self.assertEqual(["class-not-eligible"], rules(self.decisions["helper_only"]))

    def test_objects_of_the_compilation_are_reached_through_their_generated_classes(self):
        for name in ("carted", "built"):
            self.assertEqual("COMPILED", self.decisions[f"Pricing.{name}"].outcome().name(), f"{name}: {[(r.rule(), r.message()) for r in self.decisions[f'Pricing.{name}'].reasons()]}")
        carted = _uncast(list(self.bodies["carted"].body().statements())[0].value())
        total = _uncast(carted.left().left())
        self.assertEqual("InvokeJava", total.getClass().getSimpleName())
        self.assertEqual("pkg.Cart", total.owner())
        self.assertEqual("total", total.name())
        self.assertEqual(["int"], list(total.parameterTypes()))
        items = _uncast(carted.left().right())
        self.assertEqual("getItems", items.name())  # a hinted class attribute: the accessor of the generated class
        hidden = _uncast(carted.right())
        self.assertEqual("InvokePython", hidden.getClass().getSimpleName())  # not bridged: through the Python object
        self.assertEqual(2, self.bodies["carted"].stats().javaCalls())
        self.assertEqual(1, self.bodies["carted"].stats().bridgeCalls())
        built = _uncast(list(self.bodies["built"].body().statements())[0].value())
        self.assertEqual("PythonMember", built.getClass().getSimpleName())  # an instance attribute: no accessor
        self.assertEqual("NewJava", built.receiver().getClass().getSimpleName())
        self.assertEqual(["int", "java.lang.String"], list(built.receiver().parameterTypes()))

    def test_a_literal_of_mixed_elements_holds_objects(self):
        # [1, "x"] hinted list[int]: the literal holds Objects, cast through the raw type; Python checks the hint no more
        self.assertEqual("COMPILED", self.decisions["Pricing.mixed"].outcome().name(), rules(self.decisions["Pricing.mixed"]))
        returned = list(self.bodies["mixed"].body().statements())[0].value()
        self.assertEqual("Cast", returned.getClass().getSimpleName())
        self.assertEqual("java.util.List<java.lang.Object>", returned.operand().type())

    def test_sibling_calls_dispatch_to_the_stub_or_the_python_object(self):
        for name in ("sibling", "hidden_call", "via_property"):
            self.assertEqual("COMPILED", self.decisions[f"Pricing.{name}"].outcome().name(), f"{name}: {[(r.rule(), r.message()) for r in self.decisions[f'Pricing.{name}'].reasons()]}")
        sibling = _uncast(list(self.bodies["sibling"].body().statements())[0].value())
        self.assertEqual("truncate", sibling.name())
        self.assertEqual("java", sibling.dispatch())
        self.assertEqual(["int"], list(sibling.parameterTypes()))
        self.assertEqual(1, self.bodies["sibling"].stats().javaCalls())
        hidden = _uncast(list(self.bodies["hidden_call"].body().statements())[0].value())
        self.assertEqual("python", _uncast(hidden.left()).dispatch())  # not bridged: the stub has no Java method for it
        self.assertEqual("java", _uncast(hidden.right()).dispatch())  # the default the call relies on is filled in
        self.assertEqual(1, _uncast(list(_uncast(hidden.right()).arguments())[0]).value())
        self.assertEqual(1, self.bodies["hidden_call"].stats().bridgeCalls())
        via_property = _uncast(list(self.bodies["via_property"].body().statements())[0].value())
        self.assertEqual("doubled", via_property.left().property())
        self.assertEqual("double", via_property.left().type())
        self.assertTrue(via_property.left().accessor())  # read through the Python object, never a Java field

    def test_self_properties_are_read_through_the_stub(self):
        with_rate = self.bodies["with_rate"]
        product = list(with_rate.body().statements())[0].value()
        self.assertEqual("rate", product.right().property())
        self.assertEqual("double", product.right().type())
        self.assertFalse(product.right().accessor())
        self.assertEqual(1, with_rate.stats().bridgeCalls())

    def test_constructs_without_a_lowering_are_skipped_with_their_reason(self):
        expectations = {
            "builtin": "python-builtin-not-lowered",
            "power": "unbounded-integer-op",
            "retyped": "unknown-type",
            "falls_through": "unknown-type",
            "maybe_unbound": "unsupported-statement",
            "maybe": "unsupported-expression",
            "switched": "java-reserved-name",
            "leaked": "unsupported-statement",
            "raises_python": "python-exception",
            "shadowed": "python-exception",  # without Java facts the exception types are unknown; with them the order is refused
            "tried": "unsupported-statement",
            "maybe_missing": "python-builtin-not-lowered",
            "crossed": "unsupported-expression",
            "charred": "unsupported-expression",
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

    def test_static_methods_are_not_candidates_yet(self):
        decision = self.decisions["Pricing.helper"]
        self.assertEqual("NOT_CANDIDATE", decision.outcome().name())
        self.assertEqual(["static-method"], rules(decision))

    def test_loops_lower_with_flags_for_break_and_continue(self):
        summed = self.bodies["summed"]
        self.assertEqual("COMPILED", self.decisions["Pricing.summed"].outcome().name())
        self.assertEqual("COMPILED", self.decisions["Pricing.relooped"].outcome().name())
        self.assertEqual("local [result] is assigned in the try block and read after it", self.decisions["Pricing.tried"].reasons()[0].message())
        statements = list(summed.body().statements())
        loop = statements[1]
        self.assertEqual("i", loop.variable())
        self.assertTrue(loop.hasBreak())
        self.assertTrue(loop.hasContinue())
        self.assertEqual(1, loop.start().value())
        self.assertEqual(2, loop.step().value())
        countdown = self.bodies["countdown"]
        statements = list(countdown.body().statements())
        self.assertEqual("n_", statements[0].name())  # the reassigned parameter lives in a local of its own
        loop = statements[2]
        self.assertEqual(">", loop.test().op())
        self.assertEqual("n_", loop.test().left().name())
        self.assertFalse(loop.hasBreak())
        self.assertEqual("COMPILED", self.decisions["Pricing.loops"].outcome().name())
        self.assertEqual("COMPILED", self.decisions["Pricing.guarded_by_java"].outcome().name())
        tried = list(self.bodies["guarded_by_java"].body().statements())[1]  # after the shadow of n
        self.assertIsNotNone(tried.finallyBody())

    def test_collections_and_strings_lower_to_helpers(self):
        for name in ("collected", "indexed", "priced", "parsed"):
            self.assertEqual("COMPILED", self.decisions[f"Pricing.{name}"].outcome().name(), f"{name}: {[(r.rule(), r.message()) for r in self.decisions[f'Pricing.{name}'].reasons()]}")
        collected = self.bodies["collected"]
        self.assertEqual("java.util.List<java.lang.String>", collected.returnType())
        statements = list(collected.body().statements())
        self.assertEqual("names_", statements[0].name())  # the list parameter is worked on as a copy
        self.assertEqual("copy", statements[0].value().name())
        self.assertEqual("picked", statements[1].name())
        self.assertEqual("list", statements[1].value().name())
        indexed = list(self.bodies["indexed"].body().statements())  # [0] is the copy of the list parameter
        self.assertEqual("long", indexed[1].type())
        self.assertEqual("at", indexed[1].value().operand().name())
        priced = list(self.bodies["priced"].body().statements())  # [0] is the copy of the dict parameter
        self.assertEqual("contains", priced[1].test().name())
        self.assertEqual("item", list(priced[1].then().statements())[0].value().operand().name())
        parsed = list(self.bodies["parsed"].body().statements())
        self.assertEqual("java.util.List<java.lang.String>", parsed[0].value().type())
        self.assertEqual("max", parsed[-1].value().operand().name())
        counted = list(self.bodies["counted"].body().statements())  # [0] and [1] copy the parameters
        self.assertEqual("setItem", counted[2].expression().name())
        appended = list(self.bodies["appended"].body().statements())[1].value()  # after the copy of the parameter
        self.assertEqual("is None", appended.op())
        self.assertEqual("append", appended.left().name())
        self.assertEqual("none", appended.left().type())

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
        statements = list(self.bodies["bumped"].body().statements())
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


class DelegationTest(unittest.TestCase):
    def test_compiled_functions_delegate_to_the_bound_java_object(self):
        from micronaut_static import apply_delegation
        tree = ast.parse('''
class Calc:
    """The calculator."""

    def label(self, count: int, name: str = "x") -> str:
        """Labels."""
        return f"{count} x {name}"

    def other(self) -> int:
        return 1

    class Inner:
        def run(self, n: int) -> int:
            return n
''')
        targets = ["Calc#label#owner=app.Calc#kinds=int,str#retry", "Calc$Inner#run#owner=app.Calc$Inner#kinds=long#once",
                   "Missing#nothing#owner=app.Missing#kinds=#retry", "Calc#other#owner=app.Calc"]
        self.assertEqual(2, apply_delegation(tree, targets))
        self.assertEqual(0, apply_delegation(tree, targets))  # already rewritten
        source = ast.unparse(tree)
        self.assertIn("__mn_java = __class__.__micronaut_compiled__", source)
        self.assertIn("__mn_java = __mn_java.call(self, 'app.Calc', 'label', (count, name), ('int', 'str'), True)", source)
        self.assertIn("return __mn_java[0]", source)
        self.assertIn("__mn_java = __mn_java.call(self, 'app.Calc$Inner', 'run', (n,), ('long',), False)", source)
        self.assertNotIn("__dict__", source)
        calc = tree.body[0]
        self.assertIsInstance(calc.body[0], ast.Expr)  # the class docstring stays first
        self.assertEqual("__micronaut_compiled__", calc.body[1].targets[0].id)
        self.assertEqual(1, sum(1 for statement in calc.body if isinstance(statement, ast.Assign)))
        label = calc.body[2]
        self.assertIsInstance(label.body[0], ast.Expr)  # the docstring stays first
        self.assertIsInstance(label.body[1], ast.Assign)
        self.assertIsInstance(label.body[2], ast.If)
        self.assertEqual(label.lineno, label.body[1].lineno)
        other = calc.body[3]
        self.assertIsInstance(other.body[0], ast.Return)  # a target without its kinds is not delegated
        compile(tree, "delegated.py", "exec")

    def test_the_temporary_of_the_rewrite_never_shadows_a_name_of_the_function(self):
        from micronaut_static import apply_delegation
        tree = ast.parse('''
class Calc:
    def echo(self, __mn_java: str) -> str:
        return __mn_java

    def marked(self) -> int:
        __mn_java = 1
        return __mn_java
''')
        targets = ["Calc#echo#owner=app.Calc#kinds=str#retry", "Calc#marked#owner=app.Calc#kinds=#retry"]
        self.assertEqual(2, apply_delegation(tree, targets))
        self.assertEqual(0, apply_delegation(tree, targets))
        source = ast.unparse(tree)
        self.assertIn("__mn_java_1 = __class__.__micronaut_compiled__", source)
        self.assertIn("__mn_java_1 = __mn_java_1.call(self, 'app.Calc', 'echo', (__mn_java,), ('str',), True)", source)
        self.assertIn("__mn_java_1 = __mn_java_1.call(self, 'app.Calc', 'marked', (), (), True)", source)
        compile(tree, "delegated.py", "exec")

    def test_each_rewritten_method_calls_the_body_of_the_class_it_is_defined_in(self):
        from micronaut_static import apply_delegation
        tree = ast.parse('''
class A:
    def m(self) -> str:
        return "python A"


class B(A):
    def m(self) -> str:
        return "python B"

    def n(self) -> str:
        return super().m()
''')
        apply_delegation(tree, ["A#m#owner=app.A#kinds=#retry", "B#m#owner=app.B#kinds=#retry"])
        calls = []

        class Registry:
            """Binds every object: the body of each class answers with its owner's name."""

            def call(self, instance, owner, method, arguments, kinds, retry):
                calls.append(owner)
                return (f"java {owner}",)

        namespace = {}
        exec(compile(tree, "delegated.py", "exec"), namespace)
        b = namespace["B"]()
        # nothing is bound: the Python bodies run
        self.assertEqual("python A", b.n())
        namespace["A"].__micronaut_compiled__ = Registry()
        namespace["B"].__micronaut_compiled__ = namespace["A"].__micronaut_compiled__
        self.assertEqual("java app.B", b.m())
        self.assertEqual("java app.A", b.n())  # super().m() runs A's body
        self.assertEqual(["app.B", "app.A"], calls)
        self.assertNotIn("__micronaut_compiled__", vars(b))

    def test_a_function_whose_parameters_the_java_body_does_not_take_positionally_is_not_rewritten(self):
        from micronaut_static import apply_delegation
        tree = ast.parse('''
class Calc:
    def keyword(self, *, n: int) -> int:
        return n
''')
        self.assertEqual(0, apply_delegation(tree, ["Calc#keyword#owner=app.Calc#kinds=int#retry"]))

    def test_a_collection_the_java_body_returns_reaches_a_python_caller_as_a_python_one(self):
        from micronaut_static import apply_delegation
        tree = ast.parse('''
class Calc:
    def names(self) -> list[str]:
        return ["python"]

    def prices(self) -> dict[str, int]:
        return {"python": 1}
''')
        self.assertEqual(2, apply_delegation(tree, ["Calc#names#owner=app.Calc#kinds=#once#list", "Calc#prices#owner=app.Calc#kinds=#once#dict"]))

        class JavaMap:
            """A Java map, read through its interop members."""

            def keySet(self):
                return ["java"]

            def get(self, key):
                return 2

        class Registry:
            """Binds every object: the Java bodies answer with Java collections."""

            def call(self, instance, owner, method, arguments, kinds, retry):
                return (("java",) if method == "names" else JavaMap(),)

        namespace = {}
        exec(compile(tree, "delegated.py", "exec"), namespace)
        calc = namespace["Calc"]()
        namespace["Calc"].__micronaut_compiled__ = Registry()
        self.assertEqual(["java"], calc.names())
        self.assertEqual({"java": 2}, calc.prices())
    def test_a_private_method_delegates_by_the_name_the_java_delegate_has(self):
        from micronaut_static import apply_delegation
        tree = ast.parse('''
class Calc:
    def __helper(self, n: int) -> int:
        return n
''')
        self.assertEqual(1, apply_delegation(tree, ["Calc#__helper#owner=app.Calc#kinds=long#once"]))
        source = ast.unparse(tree)
        # the registry looks the Java method up by its name: an attribute read would be mangled to
        # _Calc__helper inside the class body
        self.assertIn("__mn_java.call(self, 'app.Calc', '__helper', (n,), ('long',), False)", source)
        self.assertNotIn("__mn_java.__helper", source)


SIBLINGS = '''
from jakarta.inject import Singleton


@Singleton
class Base:
    def __secret(self, n: int) -> int:
        return n

    def helper(self, n: int) -> int:
        return n + 1

    def kept(self, n: int) -> int:
        return n

    def run(self, n: int) -> int:
        return self.__secret(n) + self.helper(n) + self.kept(n)

    def checked(self, n: int) -> bool:
        return self.helper(n) is None

    def counted(self) -> int:
        return self.__count


class Inheriting(Base):
    pass


class Overriding(Inheriting):
    def helper(self, n: int) -> int:
        return n * 2
'''


class SiblingTest(unittest.TestCase):
    def setUp(self):
        self.decisions, self.planner = plan(SIBLINGS, MODE_ALL, facts=FakeFacts())
        self.bodies = {(body.className().split(".")[-1], body.methodName()): body for body in self.planner.bodies}
        decision = self.decisions["Base.run"]
        self.assertEqual("COMPILED", decision.outcome().name(), [(r.rule(), r.message()) for r in decision.reasons()])

    def test_a_private_method_is_invoked_by_its_mangled_name(self):
        run = self.bodies[("Base", "run")]
        total = _uncast(list(run.body().statements())[0].value())
        secret = _uncast(_uncast(total.left()).left())
        self.assertEqual("_Base__secret", secret.name())
        self.assertEqual("python", secret.dispatch())

    def test_only_a_subclass_that_overrides_a_method_sends_it_through_python(self):
        run = self.bodies[("Base", "run")]
        total = _uncast(list(run.body().statements())[0].value())
        self.assertEqual("python", _uncast(_uncast(total.left()).right()).dispatch())  # Overriding.helper, a grandchild's
        self.assertEqual("java", _uncast(total.right()).dispatch())  # inherited by every subclass, overridden by none

    def test_a_none_test_of_a_sibling_result_and_a_mangled_attribute_are_refused(self):
        self.assertEqual(["unsupported-expression"], rules(self.decisions["Base.checked"]))
        self.assertEqual(["unsupported-expression"], rules(self.decisions["Base.counted"]))


if __name__ == "__main__":
    unittest.main()
