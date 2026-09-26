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
from jakarta.inject import Singleton
from java.lang import Math, StringBuilder
from java.net import URL
from java.util import AbstractMap, Objects

@Singleton
class Calc:
    rate: float = 1.5

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
        return f"{a // b} {a % b} {a ** 2} {a / b} {-a}"

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

@Singleton
class Pair:
    def __init__(self, calc: Calc):
        self.calc = calc

    def has_partner(self) -> bool:
        return self.calc is not None
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
        ["same", "a", "a"], ["same", "a", "b"],
        ["rated", 2.0d],
        ["branches", true, 4], ["branches", false, 4],
        ["checked", 3], ["checked", 0],
        ["boxed", 3, 2.5d], ["boxed", 2147483647, 1e16d],
        ["signed", 1.0d, 0.1d], ["signed", -4.0d, 2.0d], ["signed", 4.0d, -2.0d],
        ["host", "http://example.com/x"], ["host", "not a url"],
        ["entry", "k", 3],
    ]

    void "compiled bodies agree with the Python bodies on every input"() {
        given:
        Map results = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            def context = buildContext(SOURCE)
            try {
                def calc = getBean(context, 'python.Calc')
                def pair = getBean(context, 'python.Pair')
                results[m] = CASES.collectEntries { List row -> [(row.toString()): invoke(calc, row)] }
                results[m]['pair'] = pair.has_partner()
                if (m == StaticCompilationMode.ALL) {
                    def compiled = decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName()
                    assert compiled.containsAll(CASES*.get(0).unique().collect { "Calc.$it".toString() } + ['Pair.has_partner']), decisions.toString()
                }
            } finally {
                context.close()
            }
        }

        expect:
        results[StaticCompilationMode.ALL] == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF][["spell", true, 1e16d, 0.1d].toString()] == "True 1e+16 0.1 None False"
        results[StaticCompilationMode.OFF][["floats", -7.5d, 2.0d].toString()] == "-4.0 0.5 56.25 -3.75 7.5"
        results[StaticCompilationMode.OFF][["ratio", 1, 0].toString()] == "raised"
        results[StaticCompilationMode.OFF][["host", "not a url"].toString()] == "raised"
        results[StaticCompilationMode.OFF][["entry", "k", 3].toString()] == "k=3"
        results[StaticCompilationMode.OFF]['pair'] == true
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
