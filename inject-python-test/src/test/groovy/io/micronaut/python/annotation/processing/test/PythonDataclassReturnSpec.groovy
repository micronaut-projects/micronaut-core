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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextExecutor
import io.micronaut.json.JsonMapper
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import org.graalvm.polyglot.Context

class PythonDataclassReturnSpec extends AbstractPythonTypeElementSpec {

    void "Java-returned dataclasses retain their protocol in a #caller context"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field, fields, is_dataclass, replace
import json

from java.util import Collections
from micronaut.serde.annotation import Serdeable


@dataclass
class BookBase:
    title: str


@Serdeable
@dataclass
class Book(BookBase):
    pages: int
    tags: list[str] = field(default_factory=list)
    note: str | None = None


@Serdeable
@dataclass
class DecoratedBook(Book):
    pass


@Serdeable
class InheritedBook(Book):
    pass


class Mixin:
    pass


@Serdeable
class SecondBaseBook(Mixin, Book):
    pass


@Serdeable
@dataclass(frozen=True, slots=True)
class FrozenBook:
    title: str
    pages: int


@Serdeable
class Note:
    title: str

    def __init__(self, title: str):
        self.title = title


class Probe:
    @staticmethod
    def check(mapper) -> str:
        for cls in (Book, DecoratedBook, InheritedBook, SecondBaseBook, FrozenBook):
            if cls is SecondBaseBook:
                # Cover second-base metadata; its Java field reconstruction/write-back is a separate limitation.
                book = Collections.singletonList(cls("Micronaut", 301)).get(0)
            else:
                book = mapper.readValue('{"title":"Micronaut","pages":301}', cls)
            # replace must work before any attribute read initializes the wrapper's Python object.
            updated = replace(book, title="Updated")
            assert type(updated) is cls
            assert updated.title == "Updated" and updated.pages == 301
            assert book.title == "Micronaut"
            assert is_dataclass(book) and is_dataclass(type(book))
            assert fields(book) == fields(cls)
            assert fields(type(book)) == fields(cls)
            assert type(book).__dataclass_params__ is cls.__dataclass_params__
            if cls is not FrozenBook:
                assert updated.note is None
                updated.tags.append("shared")
                assert list(book.tags) == ["shared"]
                book.title = "Changed"
                if cls is not SecondBaseBook:
                    assert json.loads(mapper.writeValueAsString(book))["title"] == "Changed"

        note = mapper.readValue('{"title":"plain"}', Note)
        assert not is_dataclass(note) and not is_dataclass(type(note))
        try:
            replace(note, title="Updated")
        except TypeError as error:
            assert str(error) == "replace() should be called on dataclass instances"
        else:
            raise AssertionError("an ordinary Python class was marked as a dataclass")
        return "Updated"
''', true, ["micronaut.python.pool.size": 1])
        def primary = context.getBean(Context)
        def nested = caller == "nested" ? ApplicationContext.builder()
            .classLoader(context.classLoader)
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(true))
            .environments("test")
            .properties(["micronaut.python.pool.size": 1])
            .build().start() : null
        def mapper = (nested ?: context).getBean(JsonMapper)

        when:
        def result = caller == "pooled" ? context.getBean(PythonContextExecutor).withContext { pooled ->
            assert pooled != primary
            pooled.eval("python", "Probe").invokeMember("check", mapper).asString()
        } : primary.eval("python", "Probe").invokeMember("check", mapper).asString()

        then:
        result == "Updated"

        cleanup:
        nested?.close()
        context?.close()

        where:
        caller << ["primary", "nested", "pooled"]
    }
}
