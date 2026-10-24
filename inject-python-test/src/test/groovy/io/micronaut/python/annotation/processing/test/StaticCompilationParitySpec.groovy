package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * The parity harness of the static compiler: every fixture method is compiled twice, with static
 * compilation off and on, and invoked through the Java stub with the same inputs; the results,
 * and whether an exception was raised, must agree.
 */
class StaticCompilationParitySpec extends AbstractPythonTypeElementSpec {

    StaticCompilationMode mode = StaticCompilationMode.OFF
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from dataclasses import dataclass, field
from jakarta.inject import Singleton
from java.lang import IllegalArgumentException, RuntimeException
from java.lang import Integer, Math, StringBuilder
from io.micronaut.python.annotation.processing.test import StaticOverloads
from java.net import URL, MalformedURLException
from java.time import DateTimeException
from java.util import AbstractMap, Objects


def module_twice(n: int) -> int:
    return n * 2


def module_upper(values: list[str]) -> list[str]:
    return [v.upper() for v in values if v]


def module_lengths(values: list[str]) -> dict[str, int]:
    return {v: len(v) for v in values}


def module_any(values: list[int], limit: int) -> bool:
    return any(v > limit for v in values)


class Rejected(Exception):
    """A Python exception class: generated as a RuntimeException, raised and caught as one by compiled bodies."""


class Invalid(ValueError):
    """A class extending another builtin exception: generated as a RuntimeException too."""


class Coded(Exception):
    def __init__(self, code: int):
        super().__init__(f"code {code}")
        self.code = code


class CodedSub(Coded):
    """Inherits the hinted __init__ of Coded."""


def module_rejecting(n: int) -> int:
    if n < 0:
        raise Rejected(f"module {n}")
    return n


def module_named(key: str) -> str:
    if key == "":
        raise Rejected("module empty")
    return key


MODULE_RATE = 2


def module_rated(n: int) -> int:
    return n * MODULE_RATE


def set_module_rate(rate: int) -> None:
    global MODULE_RATE
    MODULE_RATE = rate


def module_dup(n: int) -> int:
    return n + 1


def module_dup(n: int) -> int:
    return n + 2


def module_wrap(fn):
    def wrapped(n):
        return fn(n) + 1000
    return wrapped


@module_wrap
def module_wrapped(n: int) -> int:
    return n


class Scaler:
    def run(self, n: int) -> int:
        return self.scale(n)

    @staticmethod
    def scale(n: int) -> int:
        return n * 3


class QuadScaler(Scaler):
    @staticmethod
    def scale(n: int) -> int:
        return n * 4


def module_shadow(module_twice: Scaler, n: int) -> int:
    return module_twice(n)


@Singleton
class Calc:
    rate: float = 1.5
    count: int = 0

    def rejecting(self, n: int) -> int:
        if n < 0:
            raise Rejected(f"negative {n}")
        return n

    def coded(self, n: int) -> int:
        if n < 0:
            raise Coded(n)
        return n * 2

    def caught(self, n: int) -> int:
        try:
            return self.rejecting(n)
        except Rejected:
            return -1

    def caught_from_python(self, n: int) -> int:
        try:
            return self.python_rejecting(n)
        except Rejected:
            return -1

    def misordered(self, n: int) -> int:
        try:
            return self.rejecting(n)
        except RuntimeException:
            return -2
        except Rejected:
            return -1

    def java_catching(self, n: int) -> int:
        try:
            return self.rejecting(n)
        except RuntimeException:
            return -2

    def coded_sub(self, n: int) -> int:
        if n < 0:
            raise CodedSub(n)
        return n

    def python_error(self, n: int) -> Rejected:
        make = lambda v: Rejected(f"made {v}")
        return make(n)

    def raises_returned(self, n: int) -> int:
        if n < 0:
            raise self.python_error(n)
        return n

    @staticmethod
    def static_rejecting(n: int) -> int:
        if n < 0:
            raise Rejected(f"static {n}")
        return n

    def python_catching_delegated(self, n: int) -> str:
        check = lambda v: v
        try:
            return str(check(module_rejecting(n))) + "|" + str(check(Calc.static_rejecting(n)))
        except Rejected as rejected:
            return "rejected: " + str(rejected)

    def java_message(self, n: int) -> int:
        if n < 0:
            raise Rejected(StringBuilder("java").toString())
        return n

    def ordered(self, n: int) -> int:
        try:
            return self.rejecting(n)
        except Rejected:
            return -1
        except RuntimeException:
            return -2

    def python_rejecting(self, n: int) -> int:
        check = lambda v: v < 0
        if check(n):
            raise Rejected(f"python {n}")
        return n

    def python_catching(self, n: int) -> str:
        check = lambda v: v
        try:
            return str(check(self.rejecting(n)))
        except Rejected as rejected:
            return "rejected: " + str(rejected)

    def python_catching_coded(self, n: int) -> str:
        check = lambda v: v
        try:
            return str(check(self.coded(n)))
        except Coded as coded:
            return f"{coded} {coded.code} {coded.args}"

    def invalid(self, key: str) -> int:
        if key == "":
            raise Invalid(key, None, 1.5, True)
        return len(key)

    def caught_invalid(self, key: str) -> int:
        try:
            return self.invalid(key)
        except Invalid:
            return -1

    def reraised(self, n: int) -> int:
        try:
            return self.rejecting(n)
        except Rejected as rejected:
            raise rejected

    def delegated_rejecting(self, key: str) -> str:
        return module_named(key)

    def python_catching_once(self, key: str) -> str:
        check = lambda v: v
        try:
            return check(self.delegated_rejecting(key))
        except Rejected as rejected:
            return "rejected: " + str(rejected)

    def mixed_crossing(self, key: str) -> str:
        return module_named(key) if Objects.nonNull("java") else key

    def python_catching_mixed(self, key: str) -> str:
        check = lambda v: v
        try:
            return check(self.mixed_crossing(key))
        except Rejected as rejected:
            return "rejected: " + str(rejected)

    def java_in_try(self, n: int) -> int:
        try:
            return module_rejecting(n) if Objects.nonNull("java") else n
        except Rejected:
            return -1

    def total(self, quantity: int, unit_price: float) -> float:
        subtotal = quantity * unit_price
        if subtotal > 100:
            subtotal = subtotal - 5
        return subtotal

    def label(self, count: int, name: str) -> str:
        return f"{count} x {name}: {count * 2.5}"

    def helper(self, n: int) -> int:
        return n + 1

    def _hidden(self, n: int) -> int:
        return n - 1

    @property
    def factor(self) -> float:
        return self.rate * 2

    def bagged(self, n: int) -> str:
        bag = Bag(n)
        bag.rename("sack" + str(n))
        return str(bag.doubled() + bag.count + bag._secret()) + "|" + bag.label

    def chained(self, n: int) -> str:
        return str(self.helper(n) * 2 + self._hidden(n)) + "|" + str(self.factor * n) + "|" + self.label(n, "z")

    def ratio(self, a: int, b: int) -> float:
        return a / b

    def parity(self, n: int) -> str:
        return "even" if n % 2 == 0 else "odd"

    def floor(self, a: int, b: int) -> int:
        return a // b + a % b

    def floats(self, a: float, b: float) -> str:
        return f"{a // b} {a % b} {a / b} {-a}"

    def spell(self, flag: bool, x: float, y: float) -> str:
        return f"{flag} {x} {y} {None} {not flag}"

    def clamp(self, a: int, b: int) -> int:
        return Math.max(a, b)

    def build(self, name: str, n: int) -> str:
        return StringBuilder(name).append(n).toString()

    def empty(self, text: str) -> bool:
        return not text

    def choose(self, flag: bool, a: str, b: str) -> str:
        return a if flag else b

    def grouped(self, flag: bool, a: str, n: int) -> str:
        return (a if flag else "none") + "!" + str((n if flag else 0) + 1) + str(not (flag if n > 0 else False))

    def same(self, a: str, b: str) -> bool:
        return a == b and a is not None

    def rated(self, amount: float) -> float:
        return amount * self.rate

    def branches(self, flag: bool, n: int) -> int:
        if flag:
            result = n
        else:
            result = -n
        return result

    def checked(self, n: int) -> int:
        assert n > 0, f"needs a positive number, got {n}"
        return n * 2

    def boxed(self, a: int, b: float) -> str:
        return Objects.toString(a) + " " + Objects.toString(b) + " " + Objects.toString(a == 3)

    def signed(self, a: float, b: float) -> str:
        return f"{a // b} {a % b}"

    def host(self, spec: str) -> str:
        return URL(spec).getHost()

    def entry(self, key: str, value: int) -> str:
        return f"{AbstractMap.SimpleEntry(key, value).getKey()}={AbstractMap.SimpleEntry(key, value).getValue()}"

    def summed(self, n: int, skip: int, stop: int) -> int:
        total = 0
        for i in range(1, n + 1):
            if i == stop:
                break
            if i == skip:
                continue
            total += i
        return total

    def countdown(self, n: int, step: int) -> str:
        parts = ""
        for i in range(n, 0, step):
            parts = parts + str(i) + ","
        return parts

    def relooped(self, n: int) -> int:
        total = 0
        for i in range(n):
            total += i
        for i in range(n, 2 * n):
            total += i
        i = 100
        return total

    def collatz(self, n: int) -> int:
        steps = 0
        while n != 1:
            if n % 2 == 0:
                n = n // 2
            else:
                n = 3 * n + 1
            steps += 1
            if steps > 500:
                break
        return steps

    def words(self, names: list[str]) -> str:
        joined = ""
        for name in names:
            if name == "skip":
                continue
            joined = joined + name + ";"
        return joined

    def safe_host(self, spec: str) -> str:
        try:
            return URL(spec).getHost()
        except MalformedURLException as error:
            return "bad: " + error.getMessage()
        finally:
            pass

    def strict_host(self, spec: str) -> str:
        if not spec:
            raise IllegalArgumentException("empty spec")
        return URL(spec).getHost()

    def keyed(self, prices: dict[str, int]) -> int:
        total = 0
        for name in prices:
            total += len(name)
        return total

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
        return prices.get("default", -1.0)

    def parsed(self, text: str) -> str:
        parts = text.split(",")
        total = 0
        for part in parts:
            total += int(part.strip())
        return f"{max(total, 0)} {min(total, 10)} {abs(total)} {float(total) / 2}"

    def tagged(self, names: list[str]) -> dict[str, int]:
        lengths: dict[str, int] = {}
        for name in names:
            lengths[name] = len(name)
        return lengths

    def joined(self, names: list[str]) -> str:
        return ", ".join(names) + "|" + "-".join(["a", "b"]) + "|" + "x y  z".split()[2] + "|" + "abc"[1] + "|" + "abc"[-1]

    def counted(self, counts: dict[str, int], values: list[str]) -> int:
        values.append("more")
        counts["added"] = len(values)
        return counts["added"] + counts.get("x", 0)

    def unicode(self, text: str) -> str:
        return text[0] + "|" + str(len(text)) + "|" + text[-1] + "|" + text.upper() + "|" + text.lower() + "|" + str("a" in text)

    def picked(self, n: int, fallback: str) -> str:
        bag = Bag(n)
        bag.rename("" if n == 0 else "named")
        return bag.label or fallback

    def formed(self, name: str) -> str:
        form = Form(name=name)
        form.count = 4
        return form.name + str(form.count) + str(len(form.tags)) + str(Form("q", 2).count)

    def maybe_name(self, flag: bool) -> str | None:
        return "x" if flag else None

    def maybe_count(self, n: int) -> int | None:
        return n if n > 0 else None

    def sized(self, n: int) -> int | None:
        return Bag(n).maybe_size()

    def spelled_size(self, n: int) -> str:
        return str(Bag(n).maybe_size())

    def spelled_limit(self, n: int) -> str:
        return str(Bag(n).limit)

    def merged(self, extra: dict[str, str]) -> dict[str, str]:
        return {"a": "1", **extra, "z": "9"}

    def viewed(self, name: str):
        return {"name": name, "n": 1}

    def copied(self, values: list[str]) -> list[str]:
        result = list(values)
        result.append("z")
        return result

    def unhinted_name(self, name: str):
        return "<" + name + ">"

    def described(self, name: str) -> str:
        return self.unhinted_name(name) + "!"

    def rows(self) -> list:
        return self.mapped()

    def mapped(self) -> list[dict]:
        return [{"a": 1}]

    def branch_local(self, flag: bool) -> str:
        if flag:
            token = "t" + str(flag)
            return token
        return "n"

    def guarded(self, n: int) -> int:
        try:
            value = self.risky(n)
        except DateTimeException:  # an IllegalArgumentException handler would catch what compiled helpers throw: refused
            return -1
        return value + 1

    def risky(self, n: int) -> int:
        if n == 0:
            raise DateTimeException("zero")
        return n * 2

    def via_module(self, n: int) -> str:
        names = module_upper(["a", "", "b"])
        return str(module_twice(n)) + "|" + ",".join(names) + "|" + str(Calc.twice(n)) + "|" + str(self.twice(n)) + "|" + str(module_any([1, 5], n)) + "|" + str(module_lengths(names)["A"])

    @staticmethod
    def twice(n: int) -> int:
        return n * 2

    def rebound(self, n: int) -> str:
        set_module_rate(5)
        rated = module_rated(n)
        set_module_rate(2)
        return str(rated) + "|" + str(module_shadow(lambda x: x + 100, n)) + "|" + str(self.calls_rebound(n))

    def calls_rebound(self, n: int) -> int:
        return module_dup(n) + module_wrapped(n) + QuadScaler().run(n)

    def via_python(self, n: int) -> str:
        return ",".join(sorted([str(module_twice(n)), str(Calc.twice(n))], key=lambda s: s))

    def comprehended(self, values: list[int]) -> str:
        doubled = [v * 2 for v in values if v > 1]
        total = 0
        for v in doubled:
            total += v
        return str(total) + "|" + str(len({v for v in values})) + "|" + str(all(v > 0 for v in values))

    def edge(self, broken0: int) -> int:
        count = 0
        for stop0 in range(9223372036854775805, 9223372036854775807, 2):
            count += 1
            if count > 5:
                break
        return count

    def boxed_none(self, name: str) -> bool:
        return Integer.getInteger(name) is None

    def boxed_local(self, name: str) -> bool:
        value = Integer.getInteger(name)
        return value is None

    def boxed_branch(self, name: str) -> str:
        if Integer.getInteger(name) is None:
            return "none"
        return f"some {Integer.getInteger(name)}"

    def boxed_truth(self, name: str) -> bool:
        return not Integer.getInteger(name)

    def fits(self, n: int) -> bool:
        return Math.toIntExact(n * 1099511627776) is not None

    def lazy(self, x: int, y: int) -> int:
        assert x > 0, f"{10 // y}"
        return x

    def bump(self) -> int:
        self.count += 1
        return self.count

    def big_ratio(self, a: int) -> float:
        return (a * 1099511627776 + 1) / 3

    def above(self, a: int, x: float) -> bool:
        return a * 1099511627776 + 1 > x

    def below(self, a: int, x: float) -> bool:
        return x < a * 1099511627776 + 1

    def exclaim(self, name: str) -> str:
        return name + "!"

    def query(self, spec: str) -> str:
        return URL(spec).getQuery() + "?"

    # refused: each would differ from Python if compiled

    def mixed_local(self, flag: bool) -> str:
        value = 1.5
        if flag:
            value = 2
        return f"{value}"

    def mixed_conditional(self, flag: bool) -> str:
        return f"{1 if flag else 2.5}"

    def float_power(self, a: float, b: float) -> float:
        return a ** b

    def maybe_concat(self, flag: bool) -> str:
        name = "x" if flag else None
        return name + "!"

    def ambiguous(self, a: int, b: int) -> str:
        return StaticOverloads.pick(a, b)

@dataclass
class Form:
    name: str = ""
    count: int = 0
    tags: list[str] = field(default_factory=list)

@Singleton
class Pair:
    def __init__(self, calc: Calc):
        self.calc = calc
        self.builder = StringBuilder("x")

    def has_partner(self) -> bool:
        return self.calc is not None

    def built(self) -> str:
        return self.builder.append("y").toString()

class Bag:
    label: str = "bag"
    limit: int | None = None

    def __init__(self, count: int):
        self.count = count

    def doubled(self) -> int:
        return self.count * 2

    def _secret(self) -> int:
        return 7

    def maybe_size(self) -> int | None:
        return self.count if self.count > 0 else None

    def rename(self, label: str) -> None:
        self.label = label
'''

    static final List<List> CASES = [
        ["total", 3, 2.5d], ["total", 50, 2.5d],
        ["label", 2, "pen"],
        ["chained", 3], ["chained", 0],
        ["bagged", 3], ["bagged", -1],
        ["described", "ab"],
        ["via_module", 3], ["via_module", 0],
        ["comprehended", [1, 2, 3]], ["comprehended", [-1]],
        ["formed", "f"],
        ["picked", 0, "fb"], ["picked", 1, "fb"],
        ["ratio", 1, 4], ["ratio", 1, 0],
        ["parity", 3], ["parity", 8],
        ["unicode", "\uD83D\uDE00ab"], ["unicode", "i\u00DF"],
        ["floor", -7, 2], ["floor", 7, -2], ["floor", 7, 0],
        ["floats", -7.5d, 2.0d], ["floats", 1.0d, 3.0d],
        ["spell", true, 1e16d, 0.1d], ["spell", false, 2.0d, 1e-5d],
        ["clamp", 3, 9], ["clamp", -3, -9],
        ["build", "ab", 7],
        ["empty", ""], ["empty", "x"],
        ["choose", false, "a", "b"], ["choose", true, "a", "b"],
        ["grouped", true, "x", 2], ["grouped", false, "x", 0],
        ["same", "a", "a"], ["same", "a", "b"],
        ["rated", 2.0d],
        ["branches", true, 4], ["branches", false, 4],
        ["checked", 3], ["checked", 0],
        ["boxed", 3, 2.5d], ["boxed", 2147483647, 1e16d],
        ["signed", 1.0d, 0.1d], ["signed", -4.0d, 2.0d], ["signed", 4.0d, -2.0d],
        ["parsed", "1, 2,3"], ["parsed", "-7"], ["parsed", "x"],
        ["host", "http://example.com/x"], ["host", "not a url"],
        ["entry", "k", 3],
        ["summed", 10, 3, 7], ["summed", 10, 0, 0], ["summed", 0, 0, 0],
        ["countdown", 9, -3], ["countdown", 3, 0], ["countdown", 1, -1],
        ["collatz", 27], ["collatz", 1],
        ["relooped", 3], ["relooped", 0],
        ["safe_host", "http://example.com/x"], ["safe_host", "not a url"],
        ["strict_host", ""], ["strict_host", "http://example.com/x"],
        ["maybe_name", true], ["maybe_name", false],
        ["maybe_count", 3], ["maybe_count", 0],
        ["sized", 3], ["sized", 0], ["spelled_size", 3], ["spelled_size", 0], ["spelled_limit", 1],
        ["branch_local", true], ["branch_local", false],
        ["guarded", 3], ["guarded", 0],
        ["rejecting", 3], ["rejecting", -1],
        ["coded", 3], ["coded", -2],
        ["caught", 3], ["caught", -1],
        ["ordered", 3], ["ordered", -1],
        ["misordered", -1],
        ["caught_from_python", 3], ["caught_from_python", -1],
        ["python_catching", 3], ["python_catching", -1],
        ["python_catching_coded", 3], ["python_catching_coded", -2],
        ["java_catching", 3], ["java_catching", -1],
        ["coded_sub", 3], ["coded_sub", -2],
        ["raises_returned", 3], ["raises_returned", -1],
        ["python_catching_delegated", 3], ["python_catching_delegated", -1],
        ["java_message", 3], ["java_message", -1],
        ["invalid", "ab"], ["invalid", ""], ["caught_invalid", "ab"], ["caught_invalid", ""],
        ["reraised", 3], ["reraised", -1],
        ["python_catching_once", "a"], ["python_catching_once", ""],
        ["mixed_crossing", "a"], ["mixed_crossing", ""],
        ["python_catching_mixed", "a"], ["python_catching_mixed", ""],
        ["java_in_try", 3], ["java_in_try", -1],
        ["boxed_none", "no.such.property"], ["boxed_none", "micronaut.static.parity"],
        ["boxed_local", "no.such.property"], ["boxed_local", "micronaut.static.parity"],
        ["boxed_branch", "no.such.property"], ["boxed_branch", "micronaut.static.parity"],
        ["boxed_truth", "no.such.property"], ["boxed_truth", "micronaut.static.parity"],
        ["fits", 0], ["fits", 1],
        ["lazy", 1, 0], ["lazy", 0, 1],
        ["bump"],
        ["big_ratio", 8193], ["big_ratio", -8193],
        ["above", 8192, 9007199254740992.0d], ["above", 8192, Double.NaN], ["below", 8192, 9007199254740992.0d], ["below", 8192, Double.NaN],
        ["exclaim", "hi"], ["exclaim", null],
        ["query", "http://example.com/x?a=1"], ["query", "http://example.com/x"],
        ["mixed_local", true], ["mixed_local", false],
        ["mixed_conditional", true],
        ["float_power", -8.0d, 0.5d], ["float_power", 0.0d, -1.0d],
        ["maybe_concat", true],
        ["ambiguous", 1, 2],
    ]

    // the fixture methods the compiler must refuse: compiled, each would differ from its Python body
    static final Map<String, String> REFUSED = [mixed_local: "unknown-type", mixed_conditional: "unsupported-expression", float_power: "unsupported-expression",
                                                maybe_concat: "unknown-type", ambiguous: "ambiguous-overload"]

    void "compiled bodies agree with the Python bodies on every input"() {
        given:
        Map results = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            System.setProperty("micronaut.static.parity", "0")
            def context = buildContext(SOURCE)
            try {
                def calc = getBean(context, 'python.Calc')
                def pair = getBean(context, 'python.Pair')
                results[m] = CASES.collectEntries { List row -> [(row.toString()): invoke(calc, row)] }
                results[m]['pair'] = pair.has_partner()
                results[m]['built'] = pair.built()
                results[m]['via_python'] = calc.via_python(4)
                results[m]['rebound'] = calc.rebound(3)
                results[m]['words'] = calc.words(new ArrayList<>(['a', 'skip', 'b']))
                results[m]['keyed'] = calc.keyed(new LinkedHashMap<>([bb: 2, a: 1]))
                results[m]['edge'] = calc.edge(0)
                results[m]['collected'] = calc.collected(new ArrayList<>(['b', ' ', 'a', 'b', 'c']), 2)
                results[m]['indexed'] = calc.indexed(new ArrayList<>([5, 6, 7]), -1)
                results[m]['indexed-out'] = invoke(calc, ['indexed', new ArrayList<>([5]), 3])
                results[m]['priced'] = [calc.priced(new LinkedHashMap<>([tea: 1.5d]), 'tea'), calc.priced(new LinkedHashMap<>([tea: 1.5d]), 'milk')]
                results[m]['tagged'] = calc.tagged(new ArrayList<>(['a', 'bb']))
                results[m]['joined'] = calc.joined(new ArrayList<>(['x', 'y']))
                results[m]['counted'] = calc.counted(new LinkedHashMap<>([x: 1]), new ArrayList<>(['a', 'b']))
                results[m]['rows'] = calc.rows().collect { row -> row.collectEntries { k, v -> [(k.toString()): v] } }
                results[m]['merged'] = calc.merged(new LinkedHashMap<>([b: '2', a: '3'])).collectEntries { k, v -> [(k.toString()): v.toString()] }
                results[m]['viewed'] = calc.viewed('v').collectEntries { k, v -> [(k.toString()): v.toString()] }
                results[m]['copied'] = calc.copied(new ArrayList<>(['a'])).collect { it.toString() }
                results[m]['rejected'] = raisedBy { calc.rejecting(-1) }
                results[m]['coded-thrown'] = raisedBy { calc.coded(-2) }
                results[m]['coded-sub-thrown'] = raisedBy { calc.coded_sub(-2) }
                results[m]['returned-thrown'] = raisedBy { calc.raises_returned(-1) }
                results[m]['invalid-thrown'] = raisedBy { calc.invalid('') }
                results[m]['reraised-thrown'] = raisedBy { calc.reraised(-1) }
                results[m]['java-message-thrown'] = raisedBy { calc.java_message(-1) }
                PythonStatic.resetEntries()
                results[m]['once'] = calc.python_catching_once('')
                results[m]['mixed'] = calc.python_catching_mixed('')
                if (m == StaticCompilationMode.ALL) {
                    // a Python caller runs the Java body calling only the compilation, which raises the Python exception
                    // the Java one carries; a body calling Java code too keeps the Python body for Python callers
                    assert PythonStatic.entries('python.Calc#delegated_rejecting') == 1
                    assert PythonStatic.entries('python.Calc#mixed_crossing') == 0
                }
                if (m == StaticCompilationMode.ALL) {
                    def compiled = decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName()
                    assert !compiled.contains('Calc.via_python'), 'the lambda keeps via_python in Python, where module_twice delegates to its static Java method'
                    assert !compiled.containsAll(['Calc.python_rejecting', 'Calc.python_catching', 'Calc.python_catching_coded']), 'the lambdas keep the Python raisers and catchers in Python'
                    // a Java catch of RuntimeException would catch the Python exception classes, which Python lets through
                    for (String name : ['misordered', 'ordered', 'java_catching']) {
                        def refused = decisions.find { it.qualifiedName() == "Calc.$name".toString() }
                        assert refused.reasons()*.rule() == ['python-exception'] && refused.reasons()[0].message().contains('an except clause for [java.lang.RuntimeException] would catch ['), refused.toString()
                    }
                    // a Python exception that crossed the Java call is the Java class in Python, which the clause would not catch
                    def javaInTry = decisions.find { it.qualifiedName() == 'Calc.java_in_try' }
                    assert javaInTry.reasons()*.rule() == ['python-exception'] && javaInTry.reasons()[0].message().contains('the Java call [java.util.Objects.nonNull] of the try block'), javaInTry.toString()
                    // a value of a Python exception class has no Java layout: only a raise constructing it compiles
                    def returned = decisions.find { it.qualifiedName() == 'Calc.raises_returned' }
                    assert returned.reasons()*.rule() == ['python-exception'] && returned.reasons()[0].message().contains('that the raise statement does not construct'), returned.toString()
                    // what may be rebound or overridden while the program runs is not bound at compile time
                    assert !compiled.any { it in ['module_rated', 'module_dup', 'module_shadow', 'Scaler.run', 'Calc.calls_rebound'] }, decisions.toString()
                    assert compiled.contains('module_wrapped'), 'the decorated function compiles; its wrapper calls it, and it delegates to its static Java method'
                    assert compiled.containsAll((CASES*.get(0).unique() - REFUSED.keySet() - ['misordered', 'ordered', 'java_catching', 'java_in_try', 'raises_returned', 'python_catching', 'python_catching_coded', 'python_catching_delegated', 'python_catching_once', 'python_catching_mixed']).collect { "Calc.$it".toString() } + ['Pair.has_partner', 'Calc.words', 'Calc.keyed', 'Calc.edge', 'Calc.collected', 'Calc.indexed', 'Calc.priced', 'Calc.tagged', 'Calc.joined', 'Calc.counted', 'Calc.rows', 'Calc.mapped', 'Calc.merged', 'Calc.viewed', 'Calc.copied', 'Calc.unhinted_name', 'Pair.built', 'Bag.doubled', 'Calc.twice', 'module_twice', 'module_upper', 'module_lengths', 'module_any']), decisions.toString()
                    REFUSED.each { name, rule ->
                        def decision = decisions.find { it.qualifiedName() == "Calc.$name".toString() }
                        assert decision?.outcome() == StaticCompilationDecision.Outcome.SKIPPED && decision.reasons()[0].rule() == rule, decision.toString()
                    }
                }
            } finally {
                context.close()
                System.clearProperty("micronaut.static.parity")
            }
        }

        expect:
        results[StaticCompilationMode.ALL] == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF][["spell", true, 1e16d, 0.1d].toString()] == "True 1e+16 0.1 None False"
        results[StaticCompilationMode.OFF][["floats", -7.5d, 2.0d].toString()] == "-4.0 0.5 -3.75 7.5"
        results[StaticCompilationMode.OFF][["boxed_none", "no.such.property"].toString()] == true
        results[StaticCompilationMode.OFF][["boxed_none", "micronaut.static.parity"].toString()] == false
        results[StaticCompilationMode.OFF][["boxed_branch", "micronaut.static.parity"].toString()] == "some 0"
        results[StaticCompilationMode.OFF][["boxed_truth", "micronaut.static.parity"].toString()] == true
        results[StaticCompilationMode.OFF][["fits", 1].toString()] == "raised"
        results[StaticCompilationMode.OFF][["lazy", 1, 0].toString()] == 1
        results[StaticCompilationMode.OFF][["above", 8192, 9007199254740992.0d].toString()] == true
        results[StaticCompilationMode.OFF][["below", 8192, Double.NaN].toString()] == false
        results[StaticCompilationMode.OFF][["exclaim", null].toString()] == "raised"
        // a method of a Python object hinted int | None that returns None, and an attribute that holds None
        results[StaticCompilationMode.OFF][["sized", 0].toString()] == null
        results[StaticCompilationMode.OFF][["sized", 3].toString()] == 3
        results[StaticCompilationMode.OFF][["spelled_size", 0].toString()] == "None"
        results[StaticCompilationMode.OFF][["spelled_limit", 1].toString()] == "None"
        results[StaticCompilationMode.OFF][["query", "http://example.com/x"].toString()] == "raised"
        results[StaticCompilationMode.OFF][["mixed_local", true].toString()] == "2"
        results[StaticCompilationMode.OFF][["ratio", 1, 0].toString()] == "raised"
        results[StaticCompilationMode.OFF][["host", "not a url"].toString()] == "raised"
        results[StaticCompilationMode.OFF][["entry", "k", 3].toString()] == "k=3"
        results[StaticCompilationMode.OFF]['pair'] == true
        results[StaticCompilationMode.OFF]['rebound'] == '15|103|1020'
        results[StaticCompilationMode.OFF]['built'] == 'xy'
        results[StaticCompilationMode.OFF][["formed", "f"].toString()] == 'f402'
        results[StaticCompilationMode.OFF][["picked", 0, "fb"].toString()] == 'fb'
        results[StaticCompilationMode.OFF][["picked", 1, "fb"].toString()] == 'named'
        results[StaticCompilationMode.OFF]['words'] == 'a;b;'
        results[StaticCompilationMode.OFF]['keyed'] == 3
        results[StaticCompilationMode.OFF]['collected'] == ['B', 'A']
        results[StaticCompilationMode.OFF]['indexed'] == 15
        results[StaticCompilationMode.OFF]['indexed-out'] == 'raised'
        results[StaticCompilationMode.OFF]['priced'] == [1.5d, -1.0d]
        results[StaticCompilationMode.OFF]['tagged'] == [a: 1, bb: 2]
        results[StaticCompilationMode.OFF]['joined'] == 'x, y|a-b|z|b|c'
        results[StaticCompilationMode.OFF]['merged'] == [a: '3', b: '2', z: '9']
        results[StaticCompilationMode.OFF]['viewed'] == [name: 'v', n: '1']
        results[StaticCompilationMode.OFF]['copied'] == ['a', 'z']
        results[StaticCompilationMode.OFF][["parsed", "1, 2,3"].toString()] == '6 6 6 3.0'
        results[StaticCompilationMode.OFF][["parsed", "x"].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["described", "ab"].toString()] == '<ab>!'
        results[StaticCompilationMode.OFF][["via_module", 3].toString()] == '6|A,B|6|6|True|1'
        results[StaticCompilationMode.OFF]['via_python'] == '8,8'
        results[StaticCompilationMode.OFF][["comprehended", [1, 2, 3]].toString()] == '10|3|True'
        results[StaticCompilationMode.OFF]['edge'] == 1
        results[StaticCompilationMode.OFF][["guarded", 0].toString()] == -1
        results[StaticCompilationMode.OFF][["branch_local", true].toString()] == 'tTrue'
        results[StaticCompilationMode.OFF][["summed", 10, 3, 7].toString()] == 18
        results[StaticCompilationMode.OFF][["countdown", 9, -3].toString()] == '9,6,3,'
        results[StaticCompilationMode.OFF][["countdown", 3, 0].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["rejecting", -1].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["java_catching", -1].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["coded_sub", -2].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["raises_returned", -1].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["python_catching_delegated", 3].toString()] == '3|3'
        results[StaticCompilationMode.OFF][["python_catching_delegated", -1].toString()] == 'rejected: module -1'
        results[StaticCompilationMode.OFF][["caught", -1].toString()] == -1
        results[StaticCompilationMode.OFF][["caught_from_python", -1].toString()] == -1
        results[StaticCompilationMode.OFF][["python_catching", -1].toString()] == 'rejected: negative -1'
        results[StaticCompilationMode.OFF][["python_catching_coded", -2].toString()] == "code -2 -2 ('code -2',)"
        results[StaticCompilationMode.OFF]['rejected'] == 'python.Rejected: negative -1'
        results[StaticCompilationMode.OFF]['coded-thrown'] == 'python.Coded: code -2'
        results[StaticCompilationMode.OFF]['coded-sub-thrown'] == 'python.CodedSub: code -2'
        results[StaticCompilationMode.OFF][["invalid", ""].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["caught_invalid", ""].toString()] == -1
        results[StaticCompilationMode.OFF]['invalid-thrown'] == "python.Invalid: ('', None, 1.5, True)"
        results[StaticCompilationMode.OFF]['reraised-thrown'] == 'python.Rejected: negative -1'
        results[StaticCompilationMode.OFF]['java-message-thrown'] == 'python.Rejected: java'
        results[StaticCompilationMode.OFF]['once'] == 'rejected: module empty'
        results[StaticCompilationMode.OFF]['mixed'] == 'rejected: module empty'
        results[StaticCompilationMode.OFF][["java_in_try", -1].toString()] == -1
        results[StaticCompilationMode.OFF]['returned-thrown'] == 'python.Rejected: made -1'
        results[StaticCompilationMode.OFF][["collatz", 27].toString()] == 111
        results[StaticCompilationMode.OFF][["safe_host", "not a url"].toString()].startsWith('bad: ')
        results[StaticCompilationMode.OFF][["strict_host", ""].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["checked", 0].toString()] == "raised"
        results[StaticCompilationMode.OFF][["signed", 1.0d, 0.1d].toString()] == "10.0 0.09999999999999995"  // GraalPy floors the quotient; CPython would say 9.0
        results[StaticCompilationMode.OFF][["signed", -4.0d, 2.0d].toString()] == "-2.0 0.0"
        results[StaticCompilationMode.OFF][["signed", 4.0d, -2.0d].toString()] == "-2.0 -0.0"
    }

    private static Object invoke(Object bean, List row) {
        try {
            return bean."${row[0]}"(*row.tail())
        } catch (Exception ignored) {
            return "raised"
        }
    }

    /** The class and message of the exception a call throws to its Java caller. */
    private static String raisedBy(Closure call) {
        try {
            call()
            return "nothing"
        } catch (Exception e) {
            return e.class.name + ": " + e.message
        }
    }
}
