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
}
