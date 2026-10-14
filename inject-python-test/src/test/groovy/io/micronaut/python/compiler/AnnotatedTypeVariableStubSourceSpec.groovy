package io.micronaut.python.compiler

/**
 * An {@code Annotated[...]} type argument whose type is a PEP 695 type parameter names the type variable in the
 * generated stub instead of a class of the module package.
 */
class AnnotatedTypeVariableStubSourceSpec extends GeneratedJavaSourceSpec {

    void "an annotated type variable as a type argument keeps the type variable"() {
        expect:
        assertGeneratedSourceContains('''
from typing import Annotated
from jakarta.validation.constraints import NotNull
from micronaut.context.annotation import Bean, Executable

@Bean
class Repo[E]:
    @Executable
    def items(self, entities: list[Annotated[E, NotNull()]]) -> list[E]:
        return None
''', 'public List<E> items(List<E> entities)')
    }
}
