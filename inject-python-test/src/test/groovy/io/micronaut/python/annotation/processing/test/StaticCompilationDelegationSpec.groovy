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
}
