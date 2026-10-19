package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

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

    @Logged
    def stamp(self, name: str, *, mark: str = "!") -> str:
        return name + mark

    def stamped(self, name: str) -> str:
        return self.stamp(name)

@Singleton
class Caller:
    def __init__(self, calc: Calc):
        self.calc = calc

    def run(self) -> str:
        return self.calc.label(4, "pen")
'''

    void "python callers of a bound object run the java body, objects created in python keep the python body"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.python.annotation import CompileStatic

@Singleton
class Calc:
    def label(self, count: int, name: str) -> str:
        return f"{count} x {name}"

@Singleton
@CompileStatic(False)
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
        decisions.find { it.qualifiedName() == 'Caller.through_bean' }.outcome() == StaticCompilationDecision.Outcome.EXCLUDED

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

    void "a python caller catches the python exception class a java body raises, unless the body calls java code too"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton
from java.util import Objects
from micronaut.context.python.annotation import CompileStatic


class Rejected(Exception):
    pass


def rejecting(key: str) -> str:
    if key == "":
        raise Rejected("empty")
    return key


@Singleton
class Calc:
    def checked(self, key: str) -> str:
        return rejecting(key)

    def mixed(self, key: str) -> str:
        return rejecting(key) if Objects.nonNull("java") else key


@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, calc: Calc):
        self.calc = calc

    def through_checked(self, key: str) -> str:
        try:
            return self.calc.checked(key)
        except Rejected as rejected:
            return f"rejected: {rejected}"

    def through_mixed(self, key: str) -> str:
        try:
            return self.calc.mixed(key)
        except Rejected as rejected:
            return f"rejected: {rejected}"
''')
        def caller = getBean(context, 'python.Caller')

        expect:
        decisions.find { it.qualifiedName() == 'Calc.checked' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'Calc.mixed' }.outcome() == StaticCompilationDecision.Outcome.COMPILED

        when: "the Java body calls only the compilation"
        def checked = caller.through_checked('')

        then: "it ran, and its Java exception reached the Python caller as the Python exception it carries"
        checked == 'rejected: empty'
        PythonStatic.entries('python.Calc#checked') == 1

        when: "the Java body calls Java code too, where a Python exception would cross as the Java class"
        def mixed = caller.through_mixed('')

        then: "the Python caller runs the Python body"
        mixed == 'rejected: empty'
        PythonStatic.entries('python.Calc#mixed') == 0

        cleanup:
        context?.close()
    }

    void "a subclass object delegates its inherited compiled methods too"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.python.annotation import CompileStatic

class Base:
    def foo(self, n: int) -> int:
        return n + 1

@Singleton
class Sub(Base):
    def bar(self, n: int) -> int:
        return n * 2

@Singleton
@CompileStatic(False)
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
from micronaut.context.python.annotation import CompileStatic

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
@CompileStatic(False)
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

    void "an advised method is compiled: its interceptor chain runs in java from java and on the python object from python"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext(INTERCEPTED)
        def caller = getBean(context, 'python.Caller')
        def calc = getBean(context, 'python.Calc')
        def interceptor = getBean(context, 'python.LoggingInterceptor')

        expect:
        decisions.find { it.qualifiedName() == 'Calc.label' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'Calc.twice' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        calc instanceof io.micronaut.context.python.aop.StaticAdviceTarget

        when:
        def viaJava = calc.label(3, 'jar')
        def viaPython = caller.run()
        def viaSelf = calc.twice('cup')

        then: "every path ran the interceptor once and the body as Java once"
        viaJava == 'logged 3 x jar'
        viaPython == 'logged 4 x pen'
        viaSelf == 'logged 2 x cup'
        interceptor.asPolyglotValue().getMember('calls').toString() == "['label', 'label', 'label']"
        PythonStatic.entries('python.Calc#label') == 3

        and: "the compiled caller of the advised method ran as Java and called it through the Python object"
        PythonStatic.entries('python.Calc#twice') == 1

        when: "an advised sibling without a Java layout is called: Python calls it directly, without its interceptors"
        def stamped = calc.stamped('x')

        then: "the caller stays in Python, so the direct call is kept"
        stamped == 'x!'
        decisions.find { it.qualifiedName() == 'Calc.stamped' }.outcome() == StaticCompilationDecision.Outcome.SKIPPED
        decisions.find { it.qualifiedName() == 'Calc.stamped' }.reasons()*.rule() == ['sibling-call']
        interceptor.asPolyglotValue().getMember('calls').toString() == "['label', 'label', 'label']"

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
}
