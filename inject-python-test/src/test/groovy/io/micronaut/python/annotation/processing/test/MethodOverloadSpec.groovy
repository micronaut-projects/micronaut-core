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

import io.micronaut.inject.BeanDefinition
import io.micronaut.python.annotation.processing.test.overloads.GenericHandler
import io.micronaut.python.annotation.processing.test.overloads.MessageSender
import io.micronaut.python.annotation.processing.test.overloads.OptionSource
import io.micronaut.python.annotation.processing.test.overloads.StringHandler
import spock.lang.Timeout

import java.lang.reflect.Array

/**
 * Java method overloads and varargs seen from Python: same-arity overloads of an implemented
 * interface, variadic Python parameters, and the Java overload a Python list or bytes selects.
 */
class MethodOverloadSpec extends AbstractPythonTypeElementSpec {

    private static Object singleBookArray(Class<?> bookType, String title) {
        Object array = Array.newInstance(bookType, 1)
        Array.set(array, 0, bookType.getConstructor(String).newInstance(title))
        return array
    }

    void "a Python class implementing a Java interface with same-arity overloads gets every overload bridged"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.overloads import OptionSource

@Singleton
class IntegerOptions(OptionSource[int]):

    def generate(self, type_or_instance) -> list[str]:
        if isinstance(type_or_instance, int):
            return ["instance:" + str(type_or_instance)]
        return ["type:" + type_or_instance.getSimpleName()]
'''
        when:
        def context = buildContext(pythonCode)
        BeanDefinition<?> definition = getBeanDefinition(context, "python.IntegerOptions")
        OptionSource<Integer> bean = getBean(context, "python.IntegerOptions") as OptionSource<Integer>

        then: 'the stub implements both overloads with the resolved type argument'
        bean.class.getMethod("generate", Class).returnType == List
        bean.class.getMethod("generate", Integer).returnType == List
        bean.generate(Integer) == ["type:Integer"]
        bean.generate(42) == ["instance:42"]
        definition.findMethod("generate", Class).get().invoke(bean, Integer) == ["type:Integer"]

        cleanup:
        context?.close()
    }

    void "a generic and a plain interface resolving to the same Java method are bridged once"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.overloads import GenericHandler, StringHandler

@Singleton
class BothHandler(GenericHandler[str], StringHandler):

    def handle(self, value: str) -> str:
        return "handled:" + value
'''
        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.BothHandler")

        then: 'the stub declares handle(String) once (plus the javac erasure bridge) and it satisfies both interfaces'
        bean instanceof GenericHandler
        bean instanceof StringHandler
        bean.class.declaredMethods.findAll { it.name == "handle" && !it.bridge }*.parameterTypes == [[String] as Class[]]
        (bean as StringHandler).handle("a") == "handled:a"
        (bean as GenericHandler<String>).handle("b") == "handled:b"

        cleanup:
        context?.close()
    }

    void "a variadic Python parameter is a Java varargs array"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.overloads import MessageSender

@Singleton
class Sender(MessageSender):

    def send(self, topic: str, *messages: str) -> int:
        assert isinstance(messages, tuple)
        return len(messages)

    @Executable
    def joined(self, separator: str, *parts: str) -> str:
        return separator.join(parts)

    @Executable
    def count(self, *values) -> int:
        return len(values)
'''
        when:
        def context = buildContext(pythonCode)
        BeanDefinition<?> definition = getBeanDefinition(context, "python.Sender")
        def bean = getBean(context, "python.Sender")
        def joined = definition.findMethod("joined", String, String[]).get()

        then:
        joined.arguments[1].type == String[]
        joined.invoke(bean, "-", ["a", "b", "c"] as String[]) == "a-b-c"
        definition.findMethod("count", Object[]).get().invoke(bean, [[1, 2] as Object[]] as Object[]) == 2
        (bean as MessageSender).send("topic", "one", "two") == 2
        (bean as MessageSender).send("topic") == 0

        cleanup:
        context?.close()
    }

    void "a variadic parameter of an introduced method collects the positional arguments Python passes"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def BatchClient(cls):
    return cls

@Introspected
@dataclass
class Book:
    title: str

@InterceptorBean(BatchClient)
@Singleton
class BatchClientInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        books = context.getParameterValues()[0]
        return ",".join(book.title for book in books)

@BatchClient
@Singleton
class BookClient(ABC):
    @abstractmethod
    def send_books(self, *books: Book) -> str:
        ...

@Singleton
class BookSender:
    def __init__(self, client: BookClient):
        self.client = client

    @Executable
    def send_two(self) -> str:
        return self.client.send_books(Book("one"), Book("two"))

    @Executable
    def send_list(self) -> str:
        return self.client.send_books([Book("one"), Book("two"), Book("three")])

    @Executable
    def send_none(self) -> str:
        return self.client.send_books()
'''
        when:
        def context = buildContext(pythonCode)
        def sender = getBean(context, "python.BookSender")
        def client = getBean(context, "python.BookClient")
        def book = context.classLoader.loadClass("python.Book")

        then:
        sender.send_two() == "one,two"
        sender.send_list() == "one,two,three"
        sender.send_none() == ""
        client.send_books(singleBookArray(book, "four")) == "four"

        cleanup:
        context?.close()
    }

    void "an unannotated variadic parameter implements a Java varargs method"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.overloads import MessageSender

@Singleton
class UntypedSender(MessageSender):

    def send(self, topic, *messages) -> int:
        return len(topic) + len(messages)
'''
        when:
        def context = buildContext(pythonCode)
        MessageSender bean = getBean(context, "python.UntypedSender") as MessageSender

        then:
        bean.send("ab", "one", "two", "three") == 5

        cleanup:
        context?.close()
    }

    @Timeout(120)
    void "a Python list selects a Java Collection overload over a Map overload"() {
        given:
        def pythonCode = '''
import itertools
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.overloads import ResponseFactory

@Singleton
class Responses:

    @Executable
    def from_list(self) -> str:
        return ResponseFactory.success("bob", ["ROLE_A", "ROLE_B"])

    @Executable
    def from_tuple(self) -> str:
        return ResponseFactory.success("bob", ("ROLE_A",))

    @Executable
    def from_dict(self) -> str:
        return ResponseFactory.success("bob", {"email": "bob@example.com"})

    @Executable
    def list_over_map(self) -> str:
        return ResponseFactory.describe([1, 2, 3])

    @Executable
    def dict_over_list(self) -> str:
        return ResponseFactory.describe({"a": 1})

    @Executable
    def from_set(self) -> str:
        return ResponseFactory.success("bob", {"ROLE_A"})

    @Executable
    def from_dict_keys(self) -> str:
        return ResponseFactory.success("bob", {"ROLE_A": 1}.keys())

    @Executable
    def from_generator(self) -> str:
        return ResponseFactory.first(i * 2 for i in range(3))

    @Executable
    def from_infinite_iterator(self) -> str:
        return ResponseFactory.first(itertools.count(7))
'''
        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.Responses")

        then:
        bean.from_list() == "collection:bob:ROLE_A,ROLE_B"
        bean.from_tuple() == "collection:bob:ROLE_A"
        bean.from_dict() == "map:bob:1"
        bean.list_over_map() == "list:3"
        bean.dict_over_list() == "map:1"

        and: 'finite built-in containers are accepted by a Collection parameter'
        bean.from_set() == "collection:bob:ROLE_A"
        bean.from_dict_keys() == "collection:bob:ROLE_A"

        and: 'other iterables keep the lazy host view of an Iterable parameter'
        bean.from_generator() == "first:0"
        bean.from_infinite_iterator() == "first:7"

        cleanup:
        context?.close()
    }

    void "Python bytes select a Java byte array overload"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.overloads import Payloads

@Singleton
class Readers:

    @Executable
    def from_bytes(self) -> str:
        return Payloads.read(b"payload")

    @Executable
    def from_bytearray(self) -> str:
        return Payloads.read(bytearray(b"payload"))

    @Executable
    def from_str(self) -> str:
        return Payloads.read("payload")

    @Executable
    def size(self) -> int:
        return Payloads.size(b"payload")

    @Executable
    def from_list(self) -> str:
        return Payloads.read(["a", "b"])

    @Executable
    def roundtrip(self, payload: bytes) -> str:
        return Payloads.read(payload)

    @Executable
    def java_bytes(self) -> str:
        return Payloads.read(Payloads.bytes("payload"))
'''
        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.Readers")

        then:
        bean.from_bytes() == "bytes:payload"
        bean.from_bytearray() == "bytes:payload"
        bean.from_str() == "string:payload"
        bean.size() == 7
        bean.from_list() == "lines:a|b"
        bean.roundtrip("payload".bytes) == "bytes:payload"
        bean.java_bytes() == "bytes:payload"

        cleanup:
        context?.close()
    }
}
