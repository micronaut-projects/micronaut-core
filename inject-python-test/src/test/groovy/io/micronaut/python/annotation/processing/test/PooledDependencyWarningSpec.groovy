package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler

/**
 * A pooled type that depends on a singleton Python bean is warned about: the pooled type
 * exists once per context, the singleton exists once in one context, so calls through that
 * dependency run in the one context however many the pool has.
 *
 * <p>It is a warning rather than an error because the shape works and is common -- a route
 * module injecting singleton services -- so rejecting it would break code that compiles
 * today for the sake of a performance characteristic.
 */
class PooledDependencyWarningSpec extends AbstractPythonTypeElementSpec {

    private static final String SINGLETON_DEPENDENCY = '''
from jakarta.inject import Singleton
from micronaut.context.python.scope import ContextPooled

@Singleton
class NotPooled:
    def value(self) -> str:
        return "value"

@ContextPooled
class Pooled:
    def __init__(self, dependency: NotPooled):
        self.dependency = dependency
'''

    /**
     * Compiles and returns what the processor reported. Compile only: these features are about
     * what is reported, which happens whether or not a context is ever started.
     */
    private String warningsFrom(String python, String ignoreOption = null) {
        // The compiler collects its diagnostics and prints the processor's notes and warnings to
        // stderr, so that is where a `VisitorContext.warn` ends up here.
        def captured = new ByteArrayOutputStream()
        def previous = System.err
        System.setErr(new PrintStream(captured, true))
        try {
            def options = ignoreOption == null
                ? []
                : ["-Amicronaut.python.pooled.ignoreDependencies=" + ignoreOption]
            def compiler = PyronautCompiler.builder()
                .pythonCode(python)
                .options(options)
                .build()
            compiler.buildClassLoader()
        } finally {
            System.setErr(previous)
        }
        return captured.toString()
    }

    void "pooled route module depending on a singleton Python bean warns"() {
        given: "a module -- pooled, since it has routes and declares no scope -- injecting a singleton"
        def python = '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.http.annotation import Get

@Singleton
class NotPooled:
    def value(self) -> str:
        return "value"

not_pooled : Annotated[NotPooled, Inject]

@Get("/value")
def value() -> str:
    return not_pooled.value()
'''
        when:
        def reported = warningsFrom(python)

        then: "the warning names the dependency, the member it arrives through, and both ways out"
        reported.contains("depends on the singleton Python bean [NotPooled]")
        reported.contains("through [not_pooled]")
        reported.contains("Make [NotPooled] pooled as well")
        reported.contains("A dependency on a Java type has no such cost")
    }

    void "pooled class depending on a singleton Python bean warns"() {
        given:
        def python = '''
from jakarta.inject import Singleton
from micronaut.context.python.scope import ContextPooled

@Singleton
class NotPooled:
    def value(self) -> str:
        return "value"

@ContextPooled
class Pooled:
    def __init__(self, dependency: NotPooled):
        self.dependency = dependency

    def value(self) -> str:
        return self.dependency.value()
'''
        when:
        def reported = warningsFrom(python)

        then:
        reported.contains("The pooled type [Pooled] depends on the singleton Python bean [NotPooled]")
        reported.contains("through [dependency]")
    }

    void "a pooled dependency is not warned about"() {
        given: "a pooled dependency exists in every context, so there is nothing to lose"
        def python = '''
from micronaut.context.python.scope import ContextPooled

@ContextPooled
class AlsoPooled:
    def value(self) -> str:
        return "value"

@ContextPooled
class Pooled:
    def __init__(self, dependency: AlsoPooled):
        self.dependency = dependency
'''
        when:
        def reported = warningsFrom(python)

        then:
        !reported.contains("The pooled type [Pooled] depends on")
    }

    void "a Java dependency is not warned about"() {
        given: "a Java bean has no context affinity, so a pooled type may hold as many as it likes"
        def python = '''
from micronaut.context.python.scope import ContextPooled
from micronaut.core.convert import ConversionService

@ContextPooled
class Pooled:
    def __init__(self, conversions: ConversionService):
        self.conversions = conversions
'''
        when:
        def reported = warningsFrom(python)

        then:
        !reported.contains("The pooled type [Pooled] depends on")
    }

    void "an abstract Python dependency is not warned about"() {
        given: """an interface says nothing about the scope of whatever implements it, so warning
                  here would be a guess"""
        def python = '''
from typing import Protocol
from micronaut.context.python.scope import ContextPooled

class Greeter(Protocol):
    def greeting(self) -> str: ...

@ContextPooled
class Pooled:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter
'''
        when:
        def reported = warningsFrom(python)

        then:
        !reported.contains("The pooled type [Pooled] depends on")
    }

    void "a pooled class constructed with a singleton Python dependency works at runtime"() {
        given: "the warned shape, which has to keep working: it is what a route module does today"
        def python = '''
from jakarta.inject import Singleton
from micronaut.context.python.scope import ContextPooled

@Singleton
class Greeter:
    def greeting(self) -> str:
        return "hello"

@ContextPooled
class Greeting:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter

    def greet(self, name: str) -> str:
        return self.greeter.greeting() + " " + name
'''
        def context = buildContext(python, true, ["micronaut.python.pool.size": 4])
        def bean = context.getBean(context.classLoader.loadClass("python.Greeting"))

        when: "called often enough that contexts other than the singleton's serve it"
        def results = (1..40).collect { bean.greet("world") }.toSet()

        then: "the singleton is passed as its wrapper rather than rebuilt, so the call reaches it"
        results == ["hello world"] as Set

        cleanup:
        context?.close()
    }

    void "the warning can be turned off entirely"() {
        given: "an application that has weighed the cost and does not want it reported"
        when:
        def reported = warningsFrom(SINGLETON_DEPENDENCY, "false")

        then:
        !reported.contains("depends on the singleton Python bean")
    }

    void "a named dependency can be left unreported while the others still warn"() {
        given: """the point of a filter rather than a switch: a bean reached once per request loses
                  little by living in one context, and silencing it must not silence the next one"""
        def python = '''
from jakarta.inject import Singleton
from micronaut.context.python.scope import ContextPooled

@Singleton
class Accepted:
    def value(self) -> str:
        return "accepted"

@Singleton
class NotAccepted:
    def value(self) -> str:
        return "not accepted"

@ContextPooled
class Pooled:
    def __init__(self, accepted: Accepted, other: NotAccepted):
        self.accepted = accepted
        self.other = other
'''
        when:
        def reported = warningsFrom(python, "Accepted")

        then:
        !reported.contains("singleton Python bean [Accepted]")
        reported.contains("singleton Python bean [NotAccepted]")
    }
}
