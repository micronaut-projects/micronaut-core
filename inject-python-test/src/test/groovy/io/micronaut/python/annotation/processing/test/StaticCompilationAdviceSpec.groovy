package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.context.python.aop.StaticAdviceTarget
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import io.micronaut.runtime.context.scope.refresh.RefreshEvent

/**
 * The advice of compiled methods of classes: the proxy of the bean binds the interceptor chains
 * the compiled methods run in Java, whatever kind of proxy it is, whichever overload is called and
 * whichever class of a hierarchy declares the advised method.
 */
class StaticCompilationAdviceSpec extends AbstractPythonTypeElementSpec {

    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(StaticCompilationMode.ALL)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String LOGGED = '''
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
'''

    private StaticCompilationDecision.Outcome outcome(String name) {
        decisions.find { it.qualifiedName() == name }?.outcome()
    }

    private static String calls(Object interceptor) {
        interceptor.asPolyglotValue().getMember('calls').toString()
    }

    void "a compiled advised method of a refreshable bean runs its chain through the scoped proxy, before and after a refresh"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext(LOGGED + '''
from micronaut.runtime.context.scope import Refreshable

@Refreshable
class Labels:
    @Logged
    def label(self, n: int) -> str:
        return "n=" + str(n)
''')
        def labels = getBean(context, 'python.Labels')
        def interceptor = getBean(context, 'python.LoggingInterceptor')

        expect:
        outcome('Labels.label') == StaticCompilationDecision.Outcome.COMPILED
        labels instanceof StaticAdviceTarget

        when:
        def first = labels.label(1)

        then: "the interceptor ran once, the body ran once as Java"
        first == 'logged n=1'
        calls(interceptor) == "['label']"
        PythonStatic.entries('python.Labels#label') == 1

        when: "the scope is refreshed: the proxy resolves a new target"
        context.publishEvent(new RefreshEvent())
        def second = labels.label(2)

        then:
        second == 'logged n=2'
        calls(interceptor) == "['label', 'label']"
        PythonStatic.entries('python.Labels#label') == 2

        cleanup:
        context?.close()
    }

    void "the advice of a compiled method declared by a superclass is bound on the proxy of the subclass"() {
        given:
        PythonStatic.resetEntries()
        def context = buildContext(LOGGED + '''
class Base:
    @Logged
    def label(self, n: int) -> str:
        return "base " + str(n)

@Singleton
class Sub(Base):
    @Logged
    def own(self, name: str) -> str:
        return "sub " + name

    def plain(self, name: str) -> str:
        return name
''')
        def sub = getBean(context, 'python.Sub')
        def interceptor = getBean(context, 'python.LoggingInterceptor')

        expect: "the subclass inherits the advice field and binding method of its superclass"
        outcome('Base.label') == StaticCompilationDecision.Outcome.COMPILED
        outcome('Sub.own') == StaticCompilationDecision.Outcome.COMPILED
        sub instanceof StaticAdviceTarget
        Class<?> subClass = context.classLoader.loadClass('python.Sub')
        subClass.declaredFields.every { it.name != StaticAdviceTarget.FIELD }
        context.classLoader.loadClass('python.Base').declaredFields.any { it.name == StaticAdviceTarget.FIELD }

        when:
        def inherited = sub.label(3)
        def own = sub.own('x')
        def plain = sub.plain('y')

        then: "both advised methods ran their chain once and their body once as Java"
        inherited == 'logged base 3'
        own == 'logged sub x'
        plain == 'y'
        calls(interceptor) == "['label', 'own']"
        PythonStatic.entries('python.Base#label') == 1
        PythonStatic.entries('python.Sub#own') == 1

        cleanup:
        context?.close()
    }

    void "the chain of an overloaded advised method is the chain of the overload called"() {
        given: "a subclass redeclaring an advised method with another parameter type: two Java overloads of one arity"
        PythonStatic.resetEntries()
        def context = buildContext(LOGGED + '''
class Shape:
    @Logged
    def scaled(self, factor: int) -> str:
        return "int " + str(factor * 2)

@Singleton
class Square(Shape):
    @Logged
    def scaled(self, factor: str) -> str:
        return "str " + factor
''')
        def square = getBean(context, 'python.Square')
        def interceptor = getBean(context, 'python.LoggingInterceptor')

        expect:
        outcome('Square.scaled') == StaticCompilationDecision.Outcome.COMPILED

        when:
        def byString = square.scaled('x')

        then: "the overload taking a String ran, once, through its chain"
        byString == 'logged str x'
        calls(interceptor) == "['scaled']"
        PythonStatic.entries('python.Square#scaled') == 1
        PythonStatic.entries('python.Shape#scaled') == 0

        cleanup:
        context?.close()
    }
}
