package io.micronaut.python.annotation.processing.test

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
        compilerBuilder.staticCompilation(mode).staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from dataclasses import dataclass
from jakarta.inject import Singleton
from java.lang import Integer, Math, StringBuilder
from io.micronaut.python.annotation.processing.test import StaticOverloads
from java.net import URL
from java.util import AbstractMap, Objects

@Singleton
class Calc:
    rate: float = 1.5
    count: int = 0

    def total(self, quantity: int, unit_price: float) -> float:
        subtotal = quantity * unit_price
        if subtotal > 100:
            subtotal = subtotal - 5
        return subtotal

    def label(self, count: int, name: str) -> str:
        return f"{count} x {name}: {count * 2.5}"

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

    def which_none(self) -> str:
        return StaticOverloads.which(None)

    def which_str(self, value: str) -> str:
        return StaticOverloads.which(value)

    def sequence_str(self, value: str) -> str:
        return StaticOverloads.sequence(value)

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

@Singleton
class Pair:
    def __init__(self, calc: Calc):
        self.calc = calc

    def has_partner(self) -> bool:
        return self.calc is not None

@Singleton
class Sized:
    def __len__(self) -> int:
        return 0

@Singleton
class Labelled:
    def __str__(self) -> str:
        return "python"

    def toString(self) -> str:
        return "java"

@Singleton
@dataclass
class Form:
    name: str = "f"
    count: int = 3

@Singleton
class Holder:
    def __init__(self, sized: Sized, labelled: Labelled, form: Form):
        self.sized = sized
        self.labelled = labelled
        self.form = form

    # refused: each would differ from Python if compiled

    def empty(self) -> bool:
        return not self.sized

    def label(self) -> str:
        return f"{self.labelled}"

    def form_text(self) -> str:
        return str(self.form)
'''

    static final List<List> CASES = [
        ["total", 3, 2.5d], ["total", 50, 2.5d],
        ["label", 2, "pen"],
        ["ratio", 1, 4], ["ratio", 1, 0],
        ["parity", 3], ["parity", 8],
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
        ["host", "http://example.com/x"], ["host", "not a url"],
        ["entry", "k", 3],
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
        ["which_none"], ["which_str", "x"], ["sequence_str", "x"],
        ["mixed_local", true], ["mixed_local", false],
        ["mixed_conditional", true],
        ["float_power", -8.0d, 0.5d], ["float_power", 0.0d, -1.0d],
        ["maybe_concat", true],
        ["ambiguous", 1, 2],
    ]

    // the fixture methods the compiler must refuse: compiled, each would differ from its Python body
    static final Map<String, String> REFUSED = [mixed_local: "unknown-type", mixed_conditional: "unsupported-expression", float_power: "unsupported-expression",
                                                maybe_concat: "unknown-type", ambiguous: "ambiguous-overload"]
    // truthiness from __len__, a toString the stub bridges instead of __str__, a dataclass's generated __repr__
    static final Map<String, String> HOLDER_REFUSED = [empty: "truthiness-of-java-object", label: "unsupported-expression", form_text: "unsupported-expression"]

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
                def holder = getBean(context, 'python.Holder')
                results[m]['holder'] = [holder.empty(), holder.label(), holder.form_text()]
                if (m == StaticCompilationMode.ALL) {
                    def compiled = decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName()
                    assert compiled.containsAll((CASES*.get(0).unique() - REFUSED.keySet()).collect { "Calc.$it".toString() } + ['Pair.has_partner']), decisions.toString()
                    REFUSED.each { name, rule ->
                        def decision = decisions.find { it.qualifiedName() == "Calc.$name".toString() }
                        assert decision?.outcome() == StaticCompilationDecision.Outcome.SKIPPED && decision.reasons()[0].rule() == rule, decision.toString()
                    }
                    HOLDER_REFUSED.each { name, rule ->
                        def decision = decisions.find { it.qualifiedName() == "Holder.$name".toString() }
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
        results[StaticCompilationMode.OFF][["query", "http://example.com/x"].toString()] == "raised"
        results[StaticCompilationMode.OFF][["mixed_local", true].toString()] == "2"
        results[StaticCompilationMode.OFF][["ratio", 1, 0].toString()] == "raised"
        results[StaticCompilationMode.OFF][["host", "not a url"].toString()] == "raised"
        results[StaticCompilationMode.OFF][["entry", "k", 3].toString()] == "k=3"
        results[StaticCompilationMode.OFF]['pair'] == true
        results[StaticCompilationMode.OFF][["which_none"].toString()] == "String null"
        results[StaticCompilationMode.OFF][["which_str", "x"].toString()] == "String x"
        results[StaticCompilationMode.OFF][["sequence_str", "x"].toString()] == "CharSequence x"
        results[StaticCompilationMode.OFF]['holder'] == [true, "python", "Form(name='f', count=3)"]
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
}
