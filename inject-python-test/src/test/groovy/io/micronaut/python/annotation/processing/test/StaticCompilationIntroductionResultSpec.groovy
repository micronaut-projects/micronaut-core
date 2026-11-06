package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.context.python.aop.StaticAdviceTarget
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * The chain of an introduced method run by the generated class answers as the bridge does: the
 * value an interceptor returns is converted to the return type, a mismatch fails the same way, and
 * a compiled body with an advice binding no interceptor runs once.
 */
class StaticCompilationIntroductionResultSpec extends AbstractPythonTypeElementSpec {

    List<StaticCompilationDecision> decisions = []
    StaticCompilationMode mode = StaticCompilationMode.ALL

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String ANSWERS = '''
from abc import ABC, abstractmethod
from jakarta.inject import Singleton
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")


@Introduction
def Answering(cls):
    return cls


@InterceptorBean(Answering)
@Singleton
class AnswerInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext) -> object:
        name = context.getMethodName()
        if name == "length":
            return len(str(context.getParameterValueMap().get("text")))
        if name == "half":
            return len(str(context.getParameterValueMap().get("text"))) / 2
        if name == "whole":
            return 4
        if name == "fraction":
            return 2.5
        if name == "nothing":
            return None
        if name == "maybe":
            return None
        if name == "flag":
            return True
        if name == "exact":
            return 2.0
        if name == "truthy":
            return True
        if name == "found":
            return "found " + str(context.getParameterValueMap().get("key"))
        return "unknown " + name


@Answering
class Answers(ABC):
    """An abstract introduction class with a concrete member: proxied at run time, its abstract methods bind the chain."""

    @abstractmethod
    def length(self, text: str) -> int:
        pass

    @abstractmethod
    def half(self, text: str) -> float:
        pass

    @abstractmethod
    def whole(self) -> float:
        pass

    @abstractmethod
    def fraction(self) -> int:
        pass

    @abstractmethod
    def nothing(self) -> int:
        pass

    @abstractmethod
    def maybe(self) -> int | None:
        pass

    @abstractmethod
    def flag(self) -> bool:
        pass

    @abstractmethod
    def exact(self) -> int:
        pass

    @abstractmethod
    def truthy(self) -> int:
        pass

    @abstractmethod
    def found(self, key: int) -> str:
        pass

    def label(self) -> str:
        return "answers"


class Finder(ABC):
    @abstractmethod
    def found(self, key: int) -> str:
        pass

    def label(self) -> str:
        return "finder"


@Answering
class MoreAnswers(Finder):
    """Redeclares an abstract method with another parameter type: two Java overloads of one arity."""

    @abstractmethod
    def found(self, key: str) -> str:
        pass
'''

    private Map<String, Object> answer(StaticCompilationMode compilationMode) {
        mode = compilationMode
        decisions.clear()
        def context = buildContext(ANSWERS)
        try {
            def answers = getBean(context, 'python.Answers')
            def more = getBean(context, 'python.MoreAnswers')
            Map<String, Object> results = [:]
            results.bound = answers instanceof StaticAdviceTarget
            results.length = outcome { answers.length('abcd') }
            results.half = outcome { answers.half('abc') }
            results.whole = outcome { answers.whole() }
            results.fraction = outcome { answers.fraction() }
            results.nothing = outcome { answers.nothing() }
            results.maybe = outcome { answers.maybe() }
            results.flag = outcome { answers.flag() }
            results.exact = outcome { answers.exact() }
            results.truthy = outcome { answers.truthy() }
            results.found = outcome { answers.found(3) }
            results.moreByString = outcome { more.found('k') }
            results.label = outcome { answers.label() }
            return results
        } finally {
            context.close()
        }
    }

    private static Object outcome(Closure<?> call) {
        try {
            def value = call.call()
            return value == null ? 'null' : "${value.getClass().simpleName}:${value}".toString()
        } catch (Throwable e) {
            return "raised ${e.getClass().simpleName}".toString()
        }
    }

    void "an interceptor's answer is converted to the return type of the introduced method as the bridge converts it"() {
        when:
        def bridged = answer(StaticCompilationMode.OFF)
        def compiled = answer(StaticCompilationMode.ALL)

        then: "with compilation on, the generated class runs the chain and the interceptor is compiled"
        !bridged.bound
        compiled.bound
        decisions.find { it.qualifiedName() == 'AnswerInterceptor.intercept' }.outcome() == StaticCompilationDecision.Outcome.COMPILED

        and: "a Python int answers an int, a float a float, an int a float, an integral float an int; a fraction or a bool for an int and None for a primitive fail"
        compiled.length == 'Integer:4'
        compiled.half == 'Double:1.5'
        compiled.whole == 'Double:4.0'
        compiled.fraction == 'raised ClassCastException'
        compiled.nothing == 'raised NullPointerException'
        compiled.maybe == 'null'
        compiled.flag == 'Boolean:true'
        compiled.exact == 'Integer:2'
        compiled.truthy == 'raised ClassCastException'
        compiled.found == 'String:found 3'
        compiled.moreByString == 'String:found k'
        compiled.label == 'String:answers'

        and: "every answer is the bridge's"
        compiled.findAll { it.key != 'bound' } == bridged.findAll { it.key != 'bound' }
    }

    void "the abstract methods of an introduction run their chain in java only where compilation switches them on"() {
        given:
        mode = StaticCompilationMode.ANNOTATED
        def context = buildContext('''
from abc import ABC, abstractmethod
from jakarta.inject import Singleton
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.python.annotation import CompileStatic
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")


@Introduction
def Counting(cls):
    return cls


@InterceptorBean(Counting)
@Singleton
class CountingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext) -> object:
        return 3


@Counting
@CompileStatic
class Switched(ABC):
    @abstractmethod
    def count(self) -> int:
        pass

    def label(self) -> str:
        return "switched"


@Counting
class Unswitched(ABC):
    @abstractmethod
    def count(self) -> int:
        pass

    def label(self) -> str:
        return "unswitched"
''')
        def switched = getBean(context, 'python.Switched')
        def unswitched = getBean(context, 'python.Unswitched')

        expect: "the introduction the switch covers binds its chain; the other keeps the bridge, though the compilation compiles something"
        switched instanceof StaticAdviceTarget
        !(unswitched instanceof StaticAdviceTarget)
        switched.count() == 3
        unswitched.count() == 3

        cleanup:
        context?.close()
    }

    void "a compiled advised method whose advice binds no interceptor runs its body once"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.aop import Around

@Around
def Unbound(target):
    return target

@Singleton
class Plain:
    @Unbound
    def label(self, n: int) -> str:
        return "n" + str(n)
''')
        def plain = getBean(context, 'python.Plain')

        expect:
        decisions.find { it.qualifiedName() == 'Plain.label' }.outcome() == StaticCompilationDecision.Outcome.COMPILED

        when:
        def label = plain.label(3)

        then: "the advice ran no interceptor and called the body on the target, once"
        label == 'n3'
        PythonStatic.entries('python.Plain#label') == 1

        cleanup:
        context?.close()
    }

    static final String SUBTYPED = '''
from jakarta.inject import Singleton
from java.lang import IllegalArgumentException
import java

Function = java.type("java.util.function.Function")


@Singleton
class Messages(Function):
    def apply(self, value: IllegalArgumentException) -> object:
        return value.getMessage()
'''

    void "a parameter hinted with a subtype of the java parameter type is cast where the body reads it"() {
        when:
        mode = compilationMode
        decisions.clear()
        PythonStatic.resetEntries()
        def context = buildContext(SUBTYPED)
        def messages = getBean(context, 'python.Messages')
        def message = messages.apply(new IllegalArgumentException('bad'))
        def entries = PythonStatic.entries('python.Messages#apply')
        def wrong = outcome { messages.apply('not an exception') }
        context.close()

        then:
        decisions.find { it.qualifiedName() == 'Messages.apply' }?.outcome() == expected
        message == 'bad'
        entries == (compilationMode == StaticCompilationMode.ALL ? 1 : 0)
        wrong.startsWith('raised')

        where:
        compilationMode            | expected
        StaticCompilationMode.OFF  | null
        StaticCompilationMode.ALL  | StaticCompilationDecision.Outcome.COMPILED
    }

    void "an event listener adapted from a compiled bean runs once"() {
        given: "@EventListener is an @Adapter introduction of ApplicationEventListener"
        PythonStatic.resetEntries()
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.event import StartupEvent
from micronaut.runtime.event.annotation import EventListener

@Singleton
class Starter:
    def __init__(self):
        self.started = 0

    @EventListener
    def on_startup(self, event: StartupEvent) -> None:
        self.started = self.started + 1

    def count(self) -> int:
        return self.started
''')
        def starter = getBean(context, 'python.Starter')

        expect:
        starter.count() == 1

        cleanup:
        context?.close()
    }
}
