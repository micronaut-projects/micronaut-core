package io.micronaut.python.annotation.processing.test

/**
 * A pooled type that depends on a singleton Python bean is warned about: the pooled type
 * exists once per context, the singleton exists once in one context, so calls through that
 * dependency run in the one context however many the pool has.
 */
class PooledDependencyWarningSpec extends AbstractPythonTypeElementSpec {

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
        def context = buildContext(python, false)

        then: "it still compiles -- the rule is advisory, because this shape is common and works"
        context != null

        cleanup:
        context?.close()
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
        def context = buildContext(python, false)

        then:
        context != null

        cleanup:
        context?.close()
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
}
