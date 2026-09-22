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
from java.lang import IllegalArgumentException
from java.net import URL, MalformedURLException
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

    def maybe_count(self, n: int) -> int | None:
        return n if n > 0 else None

    def merged(self, extra: dict[str, str]) -> dict[str, str]:
        return {"a": "1", **extra, "z": "9"}

    def viewed(self, name: str):
        return {"name": name, "n": 1}

    def copied(self, values: list[str]) -> list[str]:
        result = list(values)
        result.append("z")
        return result

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
        except IllegalArgumentException:
            return -1
        return value + 1

    def risky(self, n: int) -> int:
        if n == 0:
            raise IllegalArgumentException("zero")
        return n * 2

    def edge(self, broken0: int) -> int:
        count = 0
        for stop0 in range(9223372036854775805, 9223372036854775807, 2):
            count += 1
            if count > 5:
                break
        return count

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
        ["maybe_count", 3], ["maybe_count", 0],
        ["branch_local", true], ["branch_local", false],
        ["guarded", 3], ["guarded", 0],
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
                if (m == StaticCompilationMode.ALL) {
                    def compiled = decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName()
                    assert compiled.containsAll(CASES*.get(0).unique().collect { "Calc.$it".toString() } + ['Pair.has_partner', 'Calc.words', 'Calc.keyed', 'Calc.edge', 'Calc.collected', 'Calc.indexed', 'Calc.priced', 'Calc.tagged', 'Calc.joined', 'Calc.counted', 'Calc.rows', 'Calc.mapped', 'Calc.merged', 'Calc.viewed', 'Calc.copied']), decisions.toString()
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
        results[StaticCompilationMode.OFF]['edge'] == 1
        results[StaticCompilationMode.OFF][["guarded", 0].toString()] == -1
        results[StaticCompilationMode.OFF][["branch_local", true].toString()] == 'tTrue'
        results[StaticCompilationMode.OFF][["summed", 10, 3, 7].toString()] == 18
        results[StaticCompilationMode.OFF][["countdown", 9, -3].toString()] == '9,6,3,'
        results[StaticCompilationMode.OFF][["countdown", 3, 0].toString()] == 'raised'
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
}
