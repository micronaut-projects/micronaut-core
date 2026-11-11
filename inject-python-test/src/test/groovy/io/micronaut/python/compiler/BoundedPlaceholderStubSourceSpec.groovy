package io.micronaut.python.compiler

class BoundedPlaceholderStubSourceSpec extends GeneratedJavaSourceSpec {

    void "bounded method type variables keep their declaration and uses"() {
        expect:
        assertGeneratedSourceContains('''
from typing import Annotated
from jakarta.validation.constraints import NotNull

class Entity:
    pass

class Repository:
    def save_all[S: Entity](self, entities: list[Annotated[S, NotNull()]]) -> list[S]:
        return entities
''', 'public <S extends Entity> List<S> save_all(List<S> entities)')
    }
}
