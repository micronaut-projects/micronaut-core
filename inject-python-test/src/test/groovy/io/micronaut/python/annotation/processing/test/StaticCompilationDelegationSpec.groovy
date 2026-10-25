package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PooledValueCoercible
import io.micronaut.context.python.PythonPool
import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

/**
 * A compiled method has one implementation: Python callers of an object bound to its stub run
 * the Java body too, an object created in Python keeps the original body, and an intercepted
 * method still runs its interceptor chain first.
 */
class StaticCompilationDelegationSpec extends AbstractPythonTypeElementSpec {

    List<StaticCompilationDecision> decisions = []
    StaticCompilationMode mode = StaticCompilationMode.ALL

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    void "control: an around interceptor on a python method runs without static compilation"() {
        given:
        mode = StaticCompilationMode.OFF
        def context = buildContext(INTERCEPTED)
        def calc = getBean(context, 'python.Calc')

        expect:
        calc.label(3, 'jar') == 'logged 3 x jar'

        cleanup:
        context?.close()
    }

    static final String INTERCEPTED = '''
from jakarta.inject import Singleton
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Around
def Logged(target):
    return target

@Singleton
@InterceptorBean(Logged)
class LoggingInterceptor(MethodInterceptor):
    def __init__(self):
        self.calls: list[str] = []

    def intercept(self, context: MethodInvocationContext):
        self.calls.append(context.getMethodName())
        return "logged " + str(context.proceed())

@Singleton
class Calc:
    @Logged
    def label(self, count: int, name: str) -> str:
        return f"{count} x {name}"

    def twice(self, name: str) -> str:
        return self.label(2, name)

@Singleton
class Caller:
    def __init__(self, calc: Calc):
        self.calc = calc

    def run(self) -> str:
        return self.calc.label(4, "pen")
'''

    void "a python caller receives the collection a delegated body returns as the python body returns it"() {
        given:
        Map results = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            PythonStatic.resetEntries()
            def context = buildContext(COLLECTIONS)
            try {
                results[m] = getBean(context, 'python.Caller').probe()
                if (m == StaticCompilationMode.ALL) {
                    assert decisions.findAll { it.qualifiedName().startsWith('Store.') && it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName() as Set == ['Store.listed', 'Store.java_listed'] as Set
                    // the Python body returns the attribute itself, which the Java body cannot give back: not delegated
                    assert PythonStatic.entries('python.Store#listed') == 0
                    // the Java list the Java body returns is the one the Python body returns
                    assert PythonStatic.entries('python.Store#java_listed') == 1
                }
            } finally {
                context.close()
            }
        }

        expect:
        results[StaticCompilationMode.OFF] == 'list True SingletonList'
        results[StaticCompilationMode.ALL] == results[StaticCompilationMode.OFF]
    }

    static final String COLLECTIONS = '''
from jakarta.inject import Singleton
from java.util import Collections

@Singleton
class Store:
    items: list[str]

    def __init__(self):
        self.items = ["a"]

    def listed(self) -> list[str]:
        return self.items

    def java_listed(self) -> list[str]:
        return Collections.singletonList("j")

@Singleton
class Caller:
    def __init__(self, store: Store):
        self.store = store

    def probe(self) -> str:
        listed = self.store.listed()
        java = self.store.java_listed()
        return f"{type(listed).__name__} {listed is self.store.items} {java.getClass().getSimpleName()}"
'''

    void "python callers of a bound object run the java body, objects created in python keep the python body"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton

@Singleton
class Calc:
    def label(self, count: int, name: str) -> str:
        return f"{count} x {name}"

@Singleton
class Caller:
    def __init__(self, calc: Calc):
        self.calc = calc

    def through_bean(self) -> str:
        return self.calc.label(2, "pen")

    def through_fresh(self) -> str:
        return Calc().label(1, "cup")
''')
        def caller = getBean(context, 'python.Caller')
        def calc = getBean(context, 'python.Calc')

        expect:
        decisions.find { it.qualifiedName() == 'Calc.label' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'Caller.through_bean' }.outcome() == StaticCompilationDecision.Outcome.SKIPPED

        when: "Python code calls the method of the injected bean"
        def viaBean = caller.through_bean()

        then: "the Java body ran"
        viaBean == '2 x pen'
        PythonStatic.entries('python.Calc#label') == 1

        when: "Python code creates its own instance and calls the method"
        def viaFresh = caller.through_fresh()

        then: "the Python body ran: nothing is bound to that object"
        viaFresh == '1 x cup'
        PythonStatic.entries('python.Calc#label') == 1

        when: "Java calls the stub"
        def viaJava = calc.label(3, 'jar')

        then:
        viaJava == '3 x jar'
        PythonStatic.entries('python.Calc#label') == 2

        cleanup:
        context?.close()
    }

    void "a subclass object delegates its inherited compiled methods too"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton

class Base:
    def foo(self, n: int) -> int:
        return n + 1

@Singleton
class Sub(Base):
    def bar(self, n: int) -> int:
        return n * 2

@Singleton
class Caller:
    def __init__(self, sub: Sub):
        self.sub = sub

    def run(self) -> str:
        return f"{self.sub.foo(1)} {self.sub.bar(2)}"
''')
        def caller = getBean(context, 'python.Caller')

        expect:
        decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName().containsAll(['Base.foo', 'Sub.bar'])

        when:
        def result = caller.run()

        then: "both the inherited and the own compiled method ran as Java"
        result == '2 4'
        PythonStatic.entries('python.Base#foo') == 1
        PythonStatic.entries('python.Sub#bar') == 1

        cleanup:
        context?.close()
    }

    void "an object of a class with several bases delegates the compiled methods of every base"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton

class Left:
    def foo(self, n: int) -> int:
        return n + 1

class Right:
    def baz(self, n: int) -> int:
        return n * 3

    def foo(self, n: int) -> int:
        return n - 1

@Singleton
class Both(Left, Right):
    def bar(self, n: int) -> int:
        return n * 2

@Singleton
class Caller:
    def __init__(self, both: Both):
        self.both = both

    def run(self) -> str:
        return f"{self.both.foo(1)} {self.both.bar(2)} {self.both.baz(3)}"
''')
        def caller = getBean(context, 'python.Caller')

        expect:
        decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName().containsAll(['Left.foo', 'Right.baz', 'Both.bar'])

        when:
        def result = caller.run()

        then: "the method of the first base wins as in Python, and the second base's own method runs as Java too"
        result == '2 4 9'
        PythonStatic.entries('python.Left#foo') == 1
        PythonStatic.entries('python.Right#foo') == 0
        PythonStatic.entries('python.Both#bar') == 1
        PythonStatic.entries('python.Right#baz') == 1

        cleanup:
        context?.close()
    }

    void "an advised method is not compiled: its interceptor chain runs on the python object from python and from java"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext(INTERCEPTED)
        def caller = getBean(context, 'python.Caller')
        def calc = getBean(context, 'python.Calc')
        def interceptor = getBean(context, 'python.LoggingInterceptor')

        expect:
        def decision = decisions.find { it.qualifiedName() == 'Calc.label' }
        decision.outcome() == StaticCompilationDecision.Outcome.NOT_CANDIDATE
        decision.reasons()*.rule() == ['intercepted-method']

        when:
        def viaJava = calc.label(3, 'jar')
        def viaPython = caller.run()
        def viaSelf = calc.twice('cup')

        then: "every path ran the interceptor once"
        viaJava == 'logged 3 x jar'
        viaPython == 'logged 4 x pen'
        viaSelf == 'logged 2 x cup'
        interceptor.asPolyglotValue().getMember('calls').toString() == "['label', 'label', 'label']"
        PythonStatic.entries('python.Calc#label') == 0

        cleanup:
        context?.close()
    }

    void "super() from a subclass runs the base's compiled body, not the override"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton

class Base:
    def m(self, n: int) -> int:
        return n + 1

@Singleton
class Sub(Base):
    def m(self, n: int) -> int:
        return n * 10

    def via_super(self, n: int):
        return super().m(n)

@Singleton
class Caller:
    def __init__(self, sub: Sub):
        self.sub = sub

    def run(self) -> str:
        return f"{self.sub.m(2)} {self.sub.via_super(2)}"
''')
        def caller = getBean(context, 'python.Caller')
        def sub = getBean(context, 'python.Sub')

        expect:
        decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName().containsAll(['Base.m', 'Sub.m'])
        decisions.find { it.qualifiedName() == 'Sub.via_super' }.outcome() != StaticCompilationDecision.Outcome.COMPILED

        when:
        def fromPython = caller.run()
        def fromJava = sub.via_super(5)

        then: "each rewritten method ran the body of its own class"
        fromPython == '20 3'
        fromJava == 6
        PythonStatic.entries('python.Sub#m') == 1
        PythonStatic.entries('python.Base#m') == 2

        cleanup:
        context?.close()
    }

    void "python callers see python exceptions and ints from compiled bodies"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton
from java.lang import System
from java.net import MalformedURLException, URL

@Singleton
class Calc:
    last: float = 0.0
    note: str = ""

    def ratio(self, a: int, b: int) -> float:
        return a / b

    def record(self, a: int, b: int) -> float:
        self.last = a / b
        return self.last

    def checked(self, n: int) -> int:
        assert n > 0, "positive"
        return n

    def noted(self, n: int) -> None:
        assert n > 0, "noted positive"
        self.note = "ok"

    def prop(self, name: str) -> str:
        return System.getProperty(name) + "!"

    def host(self, spec: str) -> str:
        return URL(spec).getHost()

    def add(self, a: int, b: int) -> int:
        return a + b

    def floor(self, a: int, b: int) -> int:
        return a // b

@Singleton
class Caller:
    def __init__(self, calc: Calc):
        self.calc = calc

    def run(self) -> str:
        out = []
        try:
            self.calc.ratio(1, 0)
        except ZeroDivisionError:
            out.append("ratio")
        try:
            self.calc.record(1, 0)
        except ZeroDivisionError as error:
            out.append(f"record {error}")
        try:
            self.calc.checked(0)
        except AssertionError as error:
            out.append(f"checked {error}")
        try:
            self.calc.noted(0)
        except AssertionError as error:
            out.append(f"noted {error}")
        try:
            self.calc.prop("micronaut.no.such.property")
        except TypeError:
            out.append("prop")
        try:
            self.calc.host("not a url")
        except MalformedURLException:
            out.append("host")
        out.append(str(self.calc.add(2 ** 62, 2 ** 62)))
        out.append(str(self.calc.add(2 ** 30, 2 ** 30)))
        out.append(str(self.calc.add(2 ** 70, 1)))
        out.append(str(self.calc.add(True, 1)))
        out.append(str(self.calc.add(1, 2)))
        out.append(str(self.calc.floor(-7, 2)))
        return ",".join(out)
''')
        def caller = getBean(context, 'python.Caller')

        expect:
        decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName()
            .containsAll(['Calc.ratio', 'Calc.record', 'Calc.checked', 'Calc.noted', 'Calc.prop', 'Calc.host', 'Calc.add', 'Calc.floor'])

        when:
        def result = caller.run()

        then: "the Python exception, or the Python body where Java would raise on a promoted int"
        result == 'ratio,record division by zero,checked positive,noted noted positive,prop,host,9223372036854775808,2147483648,1180591620717411303425,2,3,-4'

        and: "the bodies without side effects ran as Java first, those with side effects raised the Python exception themselves"
        PythonStatic.entries('python.Calc#ratio') == 1
        PythonStatic.entries('python.Calc#record') == 1
        PythonStatic.entries('python.Calc#noted') == 1
        PythonStatic.entries('python.Calc#prop') == 1
        // the checked exception of the Java call reaches the Python caller as itself
        PythonStatic.entries('python.Calc#host') == 1
        // only the ints a Java int holds reach the Java body: 2**30 + 2**30 outgrows it there, and Python answers
        PythonStatic.entries('python.Calc#add') == 2

        cleanup:
        context?.close()
    }

    void "a copied object is not bound: its compiled methods run on its own attributes"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
import copy
from jakarta.inject import Singleton

@Singleton
class Counter:
    label: str = "base"

    def __init__(self):
        self.label = "orig"

    def describe(self, suffix: str) -> str:
        return self.label + suffix

    def rename(self, name: str) -> None:
        self.label = name

@Singleton
class Copier:
    def __init__(self, counter: Counter):
        self.counter = counter

    def run(self) -> str:
        twin = copy.copy(self.counter)
        twin.rename("twin")
        deep = copy.deepcopy(self.counter)
        return " ".join([self.counter.describe("!"), twin.describe("?"), deep.describe("#"),
                         str(sorted(vars(self.counter))), str(sorted(vars(twin)))])
''')
        def copier = getBean(context, 'python.Copier')

        expect:
        decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName().containsAll(['Counter.describe', 'Counter.rename'])

        when:
        def result = copier.run()

        then: "the copies keep their own state and run the Python bodies, nothing is stored on the objects"
        result == "orig! twin? orig# ['label'] ['label']"
        PythonStatic.entries('python.Counter#describe') == 1
        PythonStatic.entries('python.Counter#rename') == 0

        cleanup:
        context?.close()
    }

    static final String INTROSPECTED = '''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.context.python.annotation import CompileStatic

@Introspected
@dataclass
class Money:
    amount: int = 0

    def doubled(self) -> int:
        return self.amount * 2

@Introspected
@dataclass(frozen=True)
class Point:
    x: int = 0

    def twice(self) -> int:
        return self.x * 2

@Singleton
@CompileStatic(False)
class Caller:
    def use(self, money: Money) -> int:
        return money.doubled()

    def use_point(self, point: Point) -> int:
        return point.twice()

    def make(self) -> Money:
        return Money(7)

    def make_point(self) -> Point:
        return Point(4)

    def fresh(self) -> int:
        return Money(7).doubled() + Point(4).twice()
'''

    private static Object newFromJava(ClassLoader classLoader, String className, int value) {
        return classLoader.loadClass(className).getDeclaredConstructor(int).newInstance(value)
    }

    void "introspected objects created from java and rebuilt from their fields are bound for python callers"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext(INTROSPECTED)
        def caller = getBean(context, 'python.Caller')

        expect:
        decisions.find { it.qualifiedName() == 'Money.doubled' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'Point.twice' }.outcome() == StaticCompilationDecision.Outcome.COMPILED

        when: "a dataclass created from Java is handed to Python code calling the compiled method"
        def money = newFromJava(context.classLoader, 'python.Money', 5)
        def fromJava = money.doubled()
        def fromPython = caller.use(money)

        then: "the Java body ran for both callers"
        fromJava == 10L
        fromPython == 10L
        PythonStatic.entries('python.Money#doubled') == 2

        when: "a frozen dataclass created from Java, rebuilt on every crossing"
        def point = newFromJava(context.classLoader, 'python.Point', 3)
        def pointFromJava = point.twice()
        def pointFromPython = caller.use_point(point)
        def pointAgain = caller.use_point(point)

        then:
        pointFromJava == 6L
        pointFromPython == 6L
        pointAgain == 6L
        PythonStatic.entries('python.Point#twice') == 3

        when: "control: objects created and used in Python keep the Python body"
        long moneyBefore = PythonStatic.entries('python.Money#doubled')
        long pointBefore = PythonStatic.entries('python.Point#twice')
        def fresh = caller.fresh()

        then:
        fresh == 22L
        PythonStatic.entries('python.Money#doubled') == moneyBefore
        PythonStatic.entries('python.Point#twice') == pointBefore

        when: "objects created in Python and wrapped by their stubs on the way to Java are bound by the wrapping"
        def made = caller.make()
        def madePoint = caller.make_point()
        def viaPython = caller.use(made)
        def pointViaPython = caller.use_point(madePoint)

        then:
        viaPython == 14L
        pointViaPython == 8L
        PythonStatic.entries('python.Money#doubled') == moneyBefore + 1
        PythonStatic.entries('python.Point#twice') == pointBefore + 1

        cleanup:
        context?.close()
    }

    void "introspected objects created from java and rebuilt in a pooled context are bound there"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext(INTROSPECTED, true, [
            "micronaut.python.pool.enabled": true,
            "micronaut.python.pool.size"   : 1
        ])
        def money = newFromJava(context.classLoader, 'python.Money', 5)
        def point = newFromJava(context.classLoader, 'python.Point', 3)

        when: "Python code of a pooled context calls the compiled methods of the objects rebuilt there"
        List<Long> results = context.getBean(PythonPool).withContext { Context targetContext ->
            Value targetMoney = ((PooledValueCoercible) money).asPolyglotValue(targetContext)
            Value targetPoint = ((PooledValueCoercible) point).asPolyglotValue(targetContext)
            [targetMoney.invokeMember('doubled').asLong(), targetPoint.invokeMember('twice').asLong()]
        }

        then: "the Java bodies ran"
        results == [10L, 6L]
        PythonStatic.entries('python.Money#doubled') == 1
        PythonStatic.entries('python.Point#twice') == 1

        cleanup:
        context?.close()
    }
}
