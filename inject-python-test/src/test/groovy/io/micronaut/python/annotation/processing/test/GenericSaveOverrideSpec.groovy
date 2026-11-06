/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test

import io.micronaut.python.annotation.processing.test.generics.Book
import io.micronaut.python.annotation.processing.test.generics.GenericSaveRepository

import java.lang.reflect.TypeVariable

/**
 * A Python method overriding an inherited method that declares its own bounded type variable
 * ({@code <S extends E> List<S> saveAll(Iterable<S>)}, the shape of the Micronaut Data
 * repositories): the generated method declares the type variable of the inherited signature,
 * whether the Python method binds the entity type or declares a PEP 695 type parameter.
 */
class GenericSaveOverrideSpec extends AbstractPythonTypeElementSpec {

    private static void assertSaveAllSignature(Object bean) {
        def saveAll = bean.class.declaredMethods.find { it.name == "saveAll" && !it.bridge }
        assert saveAll != null
        assert saveAll.parameterTypes == [Iterable] as Class[]
        TypeVariable<?>[] typeParameters = saveAll.typeParameters
        assert typeParameters*.name == ["S"]
        assert typeParameters[0].bounds == [Book] as java.lang.reflect.Type[]
        assert saveAll.genericReturnType.toString() == "java.util.List<S>"
    }

    void "an entity-typed hint adopts the inherited generic signature"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import Book, GenericSaveRepository


@Singleton
class ListRepository(GenericSaveRepository[Book]):

    def saveAll(self, entities: list[Book]) -> list[Book]:
        return list(entities)

    def save(self, entity: Book) -> Book:
        return entity
''')
        GenericSaveRepository<Book> repository = context.getBean(GenericSaveRepository) as GenericSaveRepository<Book>
        def book = new Book()

        expect:
        assertSaveAllSignature(repository)
        repository.saveAll([book]) == [book]
        repository.save(book) === book

        cleanup:
        context?.close()
    }

    void "a PEP 695 type parameter maps onto the inherited type variable"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import Book, GenericSaveRepository


@Singleton
class TypedRepository(GenericSaveRepository[Book]):

    def saveAll[S: Book](self, entities: list[S]) -> list[S]:
        return list(entities)

    def save[S: Book](self, entity: S) -> S:
        return entity
''')
        GenericSaveRepository<Book> repository = context.getBean(GenericSaveRepository) as GenericSaveRepository<Book>
        def book = new Book()

        expect:
        assertSaveAllSignature(repository)
        repository.saveAll([book]) == [book]
        repository.save(book) === book

        cleanup:
        context?.close()
    }
}
