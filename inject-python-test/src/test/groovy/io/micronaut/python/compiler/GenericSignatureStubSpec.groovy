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
package io.micronaut.python.compiler

/**
 * Generated Java stubs for Python classes implementing Java interfaces with generic
 * signatures: method type variables, wildcards, type variables of super interfaces
 * and self-referential type bounds.
 */
class GenericSignatureStubSpec extends GeneratedJavaSourceSpec {

    void "interface methods declaring their own type variables are bridged with the type variables"() {
        given:
        String source = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import GenericQueryExecutor, Book


@Singleton
class PythonQueryExecutor(GenericQueryExecutor[Book]):
    def findOne(self, builder):
        return builder.build("one")

    def findAll(self, builder):
        return [builder.build("all")]

    def informerFor(self, api_type, api_list_type, namespace):
        return lambda: None

    def count(self, entity):
        return 1
'''

        expect:
        assertGeneratedSourceContains(source, '''
public <R> R findOne(GenericQueryBuilder<R> builder) {
''')
        assertGeneratedSourceContains(source, '''
public <R> List<R> findAll(GenericQueryBuilder<R> builder) {
''')
        assertGeneratedSourceContains(source, '''
public <A extends Book, L extends List<A>> Supplier<L> informerFor(Class<A> apiType, Class<L> apiListType, String namespace) {
''')
        assertGeneratedSourceContains(source, '''
public int count(Book entity) {
''')
    }

    void "wildcard return types are bridged as declared"() {
        given:
        String source = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import GenericItemList


@Singleton
class PythonItemList(GenericItemList):
    def getItems(self):
        return []
'''

        expect:
        assertGeneratedSourceContains(source, '''
public List<? extends GenericItem> getItems() {
''')
    }

    void "the element type of a wildcard collection converts with its erasure"() {
        given:
        String source = '''
from io.micronaut.core.annotation import Introspected
from io.micronaut.python.annotation.processing.test.generics import GenericItemList, GenericItem


@Introspected
class PythonItemList(GenericItemList):
    pass
'''

        expect:
        assertGeneratedSourceContains(source, '''
PythonConversion.convertList(pythonResult, io.micronaut.python.annotation.processing.test.generics.GenericItem.class)
''')
    }

    void "a Python class extending a parameterized Predicate compiles"() {
        given:
        String source = '''
from jakarta.inject import Singleton
from java.util.function import Predicate
from io.micronaut.python.annotation.processing.test.generics import Book


@Singleton
class BookFilter(Predicate[Book]):
    def test(self, book) -> bool:
        return True
'''

        expect:
        assertGeneratedSourceContains(source, '''
public boolean test(Book book) {
''')
    }

    void "type variables of an inherited generic interface are resolved against their declaring interface"() {
        given:
        String source = '''
from jakarta.inject import Singleton
from java.util import Optional
from io.micronaut.core.bind import ArgumentBinder
from io.micronaut.python.annotation.processing.test.generics import TaggedArgumentBinder
from io.micronaut.python.compiler import Named


@Singleton
class NamedBinder(TaggedArgumentBinder[Named]):
    def getAnnotationType(self):
        return Named

    def bind(self, context, source) -> ArgumentBinder.BindingResult[object]:
        return lambda: Optional.of(source)
'''

        expect:
        assertGeneratedSourceContains(source, '''
public Class<Named> getAnnotationType() {
''')
        assertGeneratedSourceContains(source, '''
public ArgumentBinder.BindingResult<Object> bind(ArgumentConversionContext<Object> context, String source) {
''')
    }

    void "a self-referential type bound does not overflow the stub generator"() {
        given:
        String source = '''
from jakarta.inject import Singleton
from io.micronaut.context.event import BeanCreatedEventListener
from io.micronaut.python.annotation.processing.test.generics import RecursiveBuilder


@Singleton
class RecursiveBuilderListener(BeanCreatedEventListener[RecursiveBuilder]):
    def onCreated(self, event):
        return event.getBean()
'''

        expect:
        assertGeneratedSourceContains(source, '''
public RecursiveBuilder onCreated(BeanCreatedEvent<RecursiveBuilder> event) {
''')
    }

    void "a self-referential type bound referenced through java.type does not overflow the stub generator"() {
        given:
        String source = '''
import java
from jakarta.inject import Singleton
from io.micronaut.context.event import BeanCreatedEventListener

RecursiveBuilder = java.type("io.micronaut.python.annotation.processing.test.generics.RecursiveBuilder")


@Singleton
class RecursiveBuilderListener(BeanCreatedEventListener[RecursiveBuilder]):
    def onCreated(self, event):
        return event.getBean()
'''

        expect:
        assertGeneratedSourceContains(source, '''
public RecursiveBuilder onCreated(BeanCreatedEvent<RecursiveBuilder> event) {
''')
    }
}
