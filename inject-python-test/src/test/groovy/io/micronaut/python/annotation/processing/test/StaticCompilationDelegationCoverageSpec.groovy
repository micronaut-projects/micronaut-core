package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * The compiled bodies whose Python callers run the Java body too, beyond the simplest ones: each
 * feature compiles its source in modes {@code off} and {@code all}, asserts the delegation the
 * decision reports, that a Python caller ran the Java body in mode {@code all}, and that the
 * Python caller saw the same results and exceptions in both modes.
 */
class StaticCompilationDelegationCoverageSpec extends AbstractPythonTypeElementSpec {

    List<StaticCompilationDecision> decisions = []
    StaticCompilationMode mode = StaticCompilationMode.ALL

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    /**
     * Compiles the source in both modes and calls {@code Caller.probe()}, a Python method, in each.
     *
     * @param source    The source, with a {@code Caller} bean whose {@code probe()} returns a string
     * @param delegated The compiled bodies, by qualified name, with the delegation each must report
     * @return The results of the probe in mode {@code off} and in mode {@code all}, and the Java body entries of mode all
     */
    Map<String, Object> parity(String source, Map<String, String> delegated) {
        Map<String, Object> results = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            PythonStatic.resetEntries()
            def context = buildContext(source)
            try {
                results[m.optionValue()] = getBean(context, 'python.Caller').probe()
                if (m == StaticCompilationMode.ALL) {
                    delegated.each { name, status ->
                        // a module function is named by its key, package.Script#function
                        String qualified = name.contains('#') ? name.substring(name.indexOf('#') + 1) : name
                        def decision = decisions.find { it.qualifiedName() == qualified }
                        assert decision?.outcome() == StaticCompilationDecision.Outcome.COMPILED: "$name: $decision"
                        assert decision.delegation() == status
                        // Class.method counts as python.Class#method; a module function names its key itself
                        String key = name.contains('#') ? name : 'python.' + name.replaceFirst('\\.', '#')
                        results['entries.' + name] = PythonStatic.entries(key)
                    }
                }
            } finally {
                context.close()
            }
        }
        return results
    }

    void "a body reading plain and fixed attributes of self delegates in a compilation with exception classes"() {
        when:
        def results = parity('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.context.python.annotation import CompileStatic
from java.lang import Integer

class InvalidCode(Exception):
    pass

@Singleton
class Codes:
    suffix: str

    def __init__(self, base: Annotated[str, Value("${codes.base:1}")], suffix: Annotated[str, Value("${codes.suffix:0}")]):
        self.base = base
        self.suffix = suffix

    def parse(self, text: str) -> str:
        return str(Integer.parseInt(self.base + text + self.suffix))

    @CompileStatic(False)
    def change(self, suffix: str):
        self.suffix = suffix

@Singleton
class Guarded:
    @property
    def base(self) -> str:
        return "4"

    def parse(self, text: str) -> str:
        # the getter is Python code of the compilation, which may raise InvalidCode
        return str(Integer.parseInt(self.base + text))

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, codes: Codes, guarded: Guarded):
        self.codes = codes
        self.guarded = guarded

    def probe(self) -> str:
        first = self.codes.parse("2")
        self.codes.change("5")
        second = self.codes.parse("3")
        try:
            self.codes.parse("x")
            failed = "none"
        except Exception as e:
            failed = "NumberFormatException" in type(e).__name__ or "NumberFormatException" in str(e)
        return f"{first} {second} {failed} {self.guarded.parse('2')}"
''', ['Codes.parse': 'once', 'Guarded.parse': 'none(a Java exception of a Python exception class may come from the compilation or from Java code)'])

        then:
        results.off == '120 135 True 42'
        results.all == results.off
        results['entries.Codes.parse'] == 3
        results['entries.Guarded.parse'] == 0
    }

    void "a body reading an attribute of another python object of the compilation runs again in python when it fails"() {
        when:
        def results = parity('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.context.python.annotation import CompileStatic

@Singleton
class Tag:
    def __init__(self, name: Annotated[str, Value("${tag.name:red}")]):
        self.name = name

    @property
    def shout(self) -> str:
        return self.name.upper()

@Singleton
class Labels:
    def __init__(self, tag: Tag):
        self.tag = tag

    def label(self, n: int) -> str:
        return self.tag.name + ":" + str(n)

    def loud(self, n: int) -> str:
        return self.tag.shout + ":" + str(n)

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, labels: Labels):
        self.labels = labels

    def probe(self) -> str:
        return f"{self.labels.label(2)} {self.labels.label(2 ** 70)} {self.labels.loud(3)}"
''', ['Labels.label': 'retry', 'Labels.loud': 'once'])

        then:
        results.off == 'red:2 red:1180591620717411303424 RED:3'
        results.all == results.off
        // the int beyond a long is not taken: the Python body runs for it
        results['entries.Labels.label'] == 1
        results['entries.Labels.loud'] == 1
    }

    void "a body building collections with the helpers delegates and returns a new python collection"() {
        when:
        def results = parity('''
from jakarta.inject import Singleton
from micronaut.context.python.annotation import CompileStatic
from java.util import Collections

@Singleton
class Words:
    def pair(self, a: str, b: str) -> list[str]:
        both = [a, b]
        return both

    def words(self, text: str) -> list[str]:
        return text.split(",")

    def stripped(self, text: str) -> str:
        return text.strip()

    def count(self, text: str) -> int:
        return len(text.split())

    def has(self, text: str, word: str) -> bool:
        return word in text.split()

    def joined(self, text: str) -> str:
        return "-".join(text.split()) + str(text.split())

    def number(self, text: str) -> int:
        return int(text)

    def keyed(self, key: str, value: str) -> dict[str, str]:
        found = {key: value}
        return found

    def copied(self, text: str) -> list[str]:
        return list(Collections.singletonList(text))

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, words: Words):
        self.words = words

    def probe(self) -> str:
        pair = self.words.pair("a", "b")
        pair.append("c")
        again = self.words.pair("a", "b")
        copied = self.words.copied("x")
        copied.append("y")
        keyed = self.words.keyed("k", "v")
        keyed["l"] = "w"
        try:
            self.words.number("x1")
            failed = "none"
        except ValueError as e:
            failed = type(e).__name__
        return " ".join([
            type(pair).__name__, str(pair), str(again),
            str(self.words.words(" a,b ")), self.words.stripped(" s "), str(self.words.count("a b  c")), str(self.words.has("a b", "b")),
            self.words.joined("a b"), str(self.words.number("12")), str(self.words.number("99999999999999999999999")), failed,
            type(copied).__name__, str(copied), type(keyed).__name__, str(keyed),
        ])
''', ['Words.pair': 'retry', 'Words.words': 'retry', 'Words.stripped': 'retry', 'Words.count': 'retry', 'Words.has': 'retry', 'Words.joined': 'retry',
       'Words.number': 'retry', 'Words.keyed': 'retry', 'Words.copied': 'once'])

        then:
        results.off == "list ['a', 'b', 'c'] ['a', 'b'] [' a', 'b '] s 3 True a-b['a', 'b'] 12 99999999999999999999999 ValueError list ['x', 'y'] dict {'k': 'v', 'l': 'w'}"
        results.all == results.off
        results['entries.Words.pair'] == 2
        results['entries.Words.words'] == 1
        results['entries.Words.count'] == 1
        results['entries.Words.has'] == 1
        results['entries.Words.joined'] == 1
        // the int beyond a long and the text that is no int fail in Java and run again in Python
        results['entries.Words.number'] == 3
        results['entries.Words.keyed'] == 1
        results['entries.Words.copied'] == 1
    }

    void "a static method or a module function calling java runs once for python callers and fails with python's exceptions"() {
        when:
        def results = parity('''
from jakarta.inject import Singleton
from micronaut.context.python.annotation import CompileStatic
from java.lang import Integer
from java.net import URI

class Rejected(Exception):
    pass

def parsed(text: str) -> str:
    return str(Integer.parseInt(text))

def scheme(text: str) -> str:
    return URI(text).getScheme()

class Ratios:
    @staticmethod
    def ratio(text: str, d: int) -> float:
        return Integer.parseInt(text) / d

@Singleton
@CompileStatic(False)
class Caller:
    def attempt(self, call):
        try:
            return str(call())
        except ZeroDivisionError as e:
            return f"ZeroDivisionError({e}) context={e.__context__ is not None}"
        except Exception as e:
            described = type(e).__name__ + " " + str(e)
            return next((name for name in ("NumberFormatException", "URISyntaxException") if name in described), described)

    def probe(self) -> str:
        return " ".join([
            self.attempt(lambda: parsed("12")),
            self.attempt(lambda: parsed("x")),
            self.attempt(lambda: Ratios.ratio("3", 2)),
            self.attempt(lambda: Ratios.ratio("3", 0)),
            self.attempt(lambda: Ratios.ratio("y", 1)),
            self.attempt(lambda: scheme("http://a")),
            self.attempt(lambda: scheme("a b")),
        ])
''', ['python.Script#parsed': 'static-once', 'python.Script#scheme': 'static-once', 'Ratios.ratio': 'static-once'])

        then:
        results.off == '12 NumberFormatException 1.5 ZeroDivisionError(division by zero) context=False NumberFormatException http URISyntaxException'
        results.all == results.off
        results['entries.python.Script#parsed'] == 2
        results['entries.Ratios.ratio'] == 3
        results['entries.python.Script#scheme'] == 2
    }

    void "a body taking a UUID, a Locale or a Currency delegates for a python caller passing the java object"() {
        when:
        def results = parity('''
from jakarta.inject import Singleton
from micronaut.context.python.annotation import CompileStatic
from java.util import UUID, Locale, Currency

@Singleton
class Ids:
    def text(self, id: UUID) -> str:
        return id.toString()

    def tag(self, locale: Locale) -> str:
        return locale.toLanguageTag()

    def code(self, currency: Currency) -> str:
        return currency.getCurrencyCode()

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, ids: Ids):
        self.ids = ids

    def probe(self) -> str:
        id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        try:
            self.ids.text(None)
            failed = "none"
        except Exception as e:
            failed = "failed"
        return " ".join([self.ids.text(id), self.ids.tag(Locale.forLanguageTag("en-GB")), self.ids.code(Currency.getInstance("EUR")), failed])
''', ['Ids.text': 'once', 'Ids.tag': 'once', 'Ids.code': 'once'])

        then:
        results.off == '123e4567-e89b-12d3-a456-426614174000 en-GB EUR failed'
        results.all == results.off
        // None is no UUID: the Python body runs for it
        results['entries.Ids.text'] == 1
        results['entries.Ids.tag'] == 1
        results['entries.Ids.code'] == 1
    }

    void "a python object of a plain class of the compilation is taken as itself, an introspected one keeps the python body"() {
        when:
        def results = parity('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.context.python.annotation import CompileStatic

class User:
    def __init__(self, name: str):
        self.name = name
        self.visits = ""

    def visit(self, label: str) -> None:
        self.visits = self.visits + label

class Admin(User):
    pass

@Introspected
@dataclass
class Card:
    title: str

@Singleton
class Greeter:
    def greet(self, user: User) -> str:
        return "hi " + user.name

    def welcome(self, user: User) -> None:
        user.visit("w")

    def show(self, card: Card) -> str:
        return card.title

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter

    def probe(self) -> str:
        bob = User("bob")
        self.greeter.welcome(bob)
        self.greeter.welcome(bob)
        return " ".join([self.greeter.greet(bob), bob.visits, self.greeter.greet(Admin("ann")), self.greeter.show(Card("t"))])
''', ['Greeter.greet': 'retry', 'Greeter.welcome': 'once', 'Greeter.show': 'once'])

        then:
        results.off == 'hi bob ww hi ann t'
        results.all == results.off
        // an Admin is no User exactly, and an introspected Card is converted, not taken as itself: the Python body runs
        results['entries.Greeter.greet'] == 1
        results['entries.Greeter.welcome'] == 2
        results['entries.Greeter.show'] == 0
    }

    void "an int narrowed for a java call runs once: an int beyond the java parameter fails in python too"() {
        when:
        def results = parity('''
from jakarta.inject import Singleton
from micronaut.context.python.annotation import CompileStatic
from java.lang import Integer

@Singleton
class Hex:
    def hex(self, n: int) -> str:
        return Integer.toHexString(n)

    def widened(self, n: int) -> int:
        return n + 1

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, hex: Hex):
        self.hex = hex

    def probe(self) -> str:
        try:
            self.hex.hex(2 ** 40)
            failed = "none"
        except Exception:
            failed = "failed"
        return f"{self.hex.hex(255)} {failed} {self.hex.widened(2 ** 63 - 1)} {self.hex.widened(5)}"
''', ['Hex.hex': 'once', 'Hex.widened': 'retry'])

        then:
        results.off == 'ff failed 9223372036854775808 6'
        results.all == results.off
        // an int parameter of the stub takes the ints of its range only: the Python body runs for the others
        results['entries.Hex.hex'] == 1
        results['entries.Hex.widened'] == 1
    }
}
