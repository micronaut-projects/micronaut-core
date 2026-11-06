package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.python.PythonStatic
import io.micronaut.python.annotation.processing.test.flow.BookRepository
import io.micronaut.python.annotation.processing.test.flow.InMemoryBookRepository
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

import java.util.function.Function

/**
 * A compiled call of a method of a parameterized Java receiver names the method's erased descriptor:
 * a type variable the receiver binds (the T of a BeanRegistration[Foo] or an Optional[str]) is its erasure,
 * and a declared type that is no type variable (BeanDefinition<T>, Optional<U>) is that type.
 */
class StaticCompilationGenericReceiverSpec extends AbstractPythonTypeElementSpec {
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureContext(ApplicationContextBuilder builder) {
        builder.singletons(new InMemoryBookRepository())
    }

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(StaticCompilationMode.ALL)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from jakarta.inject import Singleton
from java.util import Optional
from java.util.function import Function
from io.micronaut.context import BeanRegistration
from io.micronaut.python.annotation.processing.test.flow import BookRepository


@Singleton
class Receivers:
    def definition(self, registration: BeanRegistration[BookRepository]) -> str:
        return registration.getBeanDefinition().getBeanType().getSimpleName()

    def counted(self, registration: BeanRegistration[BookRepository]) -> int:
        return registration.getBean().count()

    def mapped(self, value: Optional[str], f: Function[str, str]) -> bool:
        return value.map(f).isPresent()

    def present(self, value: Optional[str]) -> str:
        return value.orElse("none")
'''

    void "calls of methods of parameterized java receivers run compiled"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext(SOURCE)
        def receivers = getBean(context, 'python.Receivers')
        def registration = context.getBeanRegistration(BookRepository, null)
        context.getBean(BookRepository).save("Dune")

        expect: "every method is compiled"
        def names = ['Receivers.definition', 'Receivers.counted', 'Receivers.mapped', 'Receivers.present']
        def notCompiled = names.collect { name -> decisions.find { it.qualifiedName() == name } }
            .findAll { it == null || it.outcome() != StaticCompilationDecision.Outcome.COMPILED }
            .collect { "${it?.qualifiedName()}: ${it?.reasons()*.rule()} ${it?.reasons()*.message()}".toString() }
        notCompiled == []

        and: "the compiled calls resolve their methods"
        receivers.definition(registration) == 'InMemoryBookRepository'
        receivers.counted(registration) == 1
        receivers.mapped(Optional.of("dune"), { String s -> s.toUpperCase() } as Function) == true
        receivers.mapped(Optional.empty(), { String s -> s.toUpperCase() } as Function) == false
        receivers.present(Optional.of("emma")) == 'emma'
        receivers.present(Optional.empty()) == 'none'
        PythonStatic.entries('python.Receivers#definition') == 1
        PythonStatic.entries('python.Receivers#counted') == 1
        PythonStatic.entries('python.Receivers#mapped') == 2
        PythonStatic.entries('python.Receivers#present') == 2

        cleanup:
        context?.close()
    }
}
