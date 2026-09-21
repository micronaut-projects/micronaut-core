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

import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.inject.BeanIdentifier
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.python.annotation.processing.test.generics.GenericItemList
import io.micronaut.python.annotation.processing.test.generics.GenericQueryBuilder
import io.micronaut.python.annotation.processing.test.generics.GenericQueryExecutor
import io.micronaut.python.annotation.processing.test.generics.RecursiveBuilder
import io.micronaut.python.annotation.processing.test.generics.TaggedArgumentBinder

import java.util.function.Predicate

/**
 * Python beans implementing Java interfaces with generic signatures, called through the
 * generated Java stubs.
 */
class GenericSignatureBridgeSpec extends AbstractPythonTypeElementSpec {

    void "a Python bean implements interface methods declaring their own type variables"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import GenericQueryExecutor, Book


@Singleton
class PythonQueryExecutor(GenericQueryExecutor[Book]):
    def findOne(self, builder):
        return builder.build("one")

    def findAll(self, builder):
        return [builder.build("first"), builder.build("second")]

    def informerFor(self, api_type, api_list_type, namespace):
        return lambda: [namespace]

    def count(self, entity):
        return 1
''')
        GenericQueryExecutor<?> executor = context.getBean(GenericQueryExecutor)
        GenericQueryBuilder<String> builder = { String query -> query.toUpperCase() } as GenericQueryBuilder<String>

        expect:
        executor.findOne(builder) == "ONE"
        executor.findAll(builder) == ["FIRST", "SECOND"]
        executor.informerFor(Object, List, "default").get() == ["default"]
        executor.count(null) == 1

        cleanup:
        context.close()
    }

    void "a Python bean implements a wildcard return type"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import GenericItemList, GenericItem


@Singleton
class PythonItem(GenericItem):
    def getKind(self) -> str:
        return "python"


@Singleton
class PythonItemList(GenericItemList):
    def __init__(self, item: PythonItem):
        self.item = item

    def getItems(self):
        return [self.item]
''')
        GenericItemList itemList = context.getBean(GenericItemList)

        expect:
        itemList.items*.kind == ["python"]

        cleanup:
        context.close()
    }

    void "a Python bean extends a parameterized Predicate"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from java.util.function import Predicate
from io.micronaut.python.annotation.processing.test.generics import Book


@Singleton
class BookFilter(Predicate[Book]):
    def test(self, book) -> bool:
        return book is not None
''')
        Predicate<?> filter = context.getBean(Predicate)

        expect:
        context.getBeanDefinition(Predicate).getTypeArguments(Predicate)*.type*.simpleName == ["Book"]
        filter.test(new io.micronaut.python.annotation.processing.test.generics.Book())
        !filter.test(null)
        !Predicate.not(filter).test(new io.micronaut.python.annotation.processing.test.generics.Book())

        cleanup:
        context.close()
    }

    void "a Python bean implements a generic interface whose value type is fixed by a super interface"() {
        given:
        def context = buildContext('''
import java
from jakarta.inject import Singleton
from java.util import Optional
from io.micronaut.core.bind import ArgumentBinder
from io.micronaut.python.annotation.processing.test.generics import TaggedArgumentBinder
from io.micronaut.python.compiler import Named


@Singleton
class NamedBinder(TaggedArgumentBinder[Named]):
    def getAnnotationType(self):
        return java.type("io.micronaut.python.compiler.Named")

    def bind(self, context, source) -> ArgumentBinder.BindingResult[object]:
        return lambda: Optional.of(source + "!")
''')
        TaggedArgumentBinder<?> binder = context.getBean(TaggedArgumentBinder)
        ArgumentConversionContext<Object> conversionContext = ConversionContext.of(Argument.OBJECT_ARGUMENT)

        expect:
        binder.annotationType.simpleName == "Named"
        binder.bind(conversionContext, "value").value.get() == "value!"
        context.getBeanDefinition(TaggedArgumentBinder).getTypeArguments(ArgumentBinder)*.type == [Object, String]

        cleanup:
        context.close()
    }

    void "a Python bean listens for a type with a self-referential type bound"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.context.event import BeanCreatedEventListener
from io.micronaut.python.annotation.processing.test.generics import RecursiveBuilder


@Singleton
class RecursiveBuilderListener(BeanCreatedEventListener[RecursiveBuilder]):
    def onCreated(self, event):
        return event.getBean().name("listened")
''')
        BeanCreatedEventListener<RecursiveBuilder> listener = context.getBean(BeanCreatedEventListener)
        RecursiveBuilder<String, ?> builder = new StringBuilderLike()

        expect:
        context.getBeanDefinition(BeanCreatedEventListener).getTypeArguments(BeanCreatedEventListener)*.type == [RecursiveBuilder]
        listener.onCreated(new BeanCreatedEvent<>(context, null, BeanIdentifier.of("builder"), Argument.of(RecursiveBuilder), builder)).build() == "listened"

        cleanup:
        context.close()
    }

    static class StringBuilderLike implements RecursiveBuilder<String, StringBuilderLike> {
        String name

        @Override
        StringBuilderLike name(String name) {
            this.name = name
            return this
        }

        @Override
        String build() {
            return name
        }
    }
}
