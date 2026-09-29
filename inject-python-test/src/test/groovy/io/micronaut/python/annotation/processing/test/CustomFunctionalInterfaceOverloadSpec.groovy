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

import io.micronaut.aop.Interceptor
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.python.annotation.processing.test.specifications.SpecificationInterceptor
import io.micronaut.python.annotation.processing.test.specifications.SpecificationOverloads
import org.graalvm.polyglot.Value

class CustomFunctionalInterfaceOverloadSpec extends AbstractPythonTypeElementSpec {

    private static final String SOURCE = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.context.python import PythonInterop
from micronaut.python.annotation.processing.test.specifications import PredicateSpec, SpecificationOverloads


def name_equals(name):
    return lambda entity, total: entity == name


def set_new_name(new_name, where):
    def specification(entity, index, total):
        return new_name if where(entity, total) else None
    return specification


@Singleton
class RepositoryCaller:
    def __init__(self, repository: SpecificationOverloads):
        self.repository = repository

    @Executable
    def plain_object(self) -> str:
        # created here rather than injected: the compiler found the interfaces in the imported type
        return str(SpecificationOverloads().deleteAll(name_equals("Denis")))

    @Executable
    def plain_object_adapted(self) -> str:
        return str(SpecificationOverloads().deleteAll(PythonInterop.fn(PredicateSpec, name_equals("Denis"))))

    @Executable
    def delete_by_specification(self) -> str:
        deleted = self.repository.deleteAll(name_equals("Josh"))
        return f"{deleted}:{list(self.repository.people())}"

    @Executable
    def delete_by_names(self) -> str:
        deleted = self.repository.deleteAll(["Josh"])
        return f"{deleted}:{list(self.repository.people())}"

    @Executable
    def update_by_specification(self) -> str:
        updated = self.repository.updateAll(set_new_name("Steven", where=name_equals("Denis")))
        return f"{updated}:{list(self.repository.people())}"

    @Executable
    def update_by_names(self) -> str:
        return str(self.repository.updateAll(("a", "b", "c")))

    @Executable
    def describe_specification(self) -> str:
        return self.repository.describe(lambda entity, total: entity == "x")

    @Executable
    def describe_dict(self) -> str:
        return self.repository.describe({"a": "b"})

    @Executable
    def find_one_predicate(self) -> str:
        return self.repository.findOne(name_equals("Josh")).orElse("none")

    @Executable
    def find_one_query(self) -> str:
        return self.repository.findOne(lambda entity, query, total: query == "query" and entity == "Denis").orElse("none")

    @Executable
    def count(self) -> int:
        return self.repository.count(lambda entity, total: total == 2)

    @Executable
    def subscribe_publisher(self) -> str:
        # Publisher is a single abstract method interface without @FunctionalInterface: subscribe(Subscriber)
        return self.repository.subscribe(lambda subscriber: subscriber.onNext("from lambda"))

    @Executable
    def subscribe_names(self) -> str:
        return self.repository.subscribe(["a", "b"])
'''

    private static final String REPOSITORY_SOURCE = '''
from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.specifications import PredicateSpec, SpecificationAdvice, SpecificationRepository, UpdateSpec


def name_equals(name):
    return lambda entity, total: entity == name


def set_new_name(new_name, where):
    def specification(entity, index, total):
        return new_name if where(entity, total) else None
    return specification


@SpecificationAdvice
@Singleton
class PersonRepository(SpecificationRepository[str]):

    def updateAll(self, spec: UpdateSpec[str]) -> int: ...

    def deleteAll(self, spec: PredicateSpec[str]) -> int: ...


@Singleton
class RepositoryCaller:

    repository: Annotated[PersonRepository, Inject]

    @Executable
    def delete_by_specification(self) -> int:
        return self.repository.deleteAll(name_equals("Denis"))

    @Executable
    def delete_by_names(self) -> int:
        return self.repository.deleteAll(["Josh", "Denis"])

    @Executable
    def update_by_specification(self) -> int:
        return self.repository.updateAll(set_new_name("Steven", where=name_equals("Denis")))

    @Executable
    def find_one_predicate(self) -> str:
        return self.repository.findOne(name_equals("Josh")).orElse("none")

    @Executable
    def find_one_query(self) -> str:
        return self.repository.findOne(lambda entity, query, total: query == "query" and entity == "Denis").orElse("none")

    @Executable
    def count(self) -> int:
        return self.repository.count(lambda entity, total: total == 2)
'''

    void "a Python callable selects the specification overload over the Iterable, Collection and Map ones"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.RepositoryCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('update_by_specification').asString() == "1:['Steven', 'Josh']"
        caller.invokeMember('describe_specification').asString() == 'spec:true'
        caller.invokeMember('count').asInt() == 2
        caller.invokeMember('delete_by_specification').asString() == "1:['Steven']"

        cleanup:
        ctx?.close()
    }

    void "the functional interfaces of an imported type are selected on any object of it, and PythonInterop.fn still adapts explicitly"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.RepositoryCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('plain_object').asString() == '1'
        caller.invokeMember('plain_object_adapted').asString() == '1'

        cleanup:
        ctx?.close()
    }

    void "a Python sequence or dict still selects the Iterable, Collection and Map overloads"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.RepositoryCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('delete_by_names').asString() == "1:['Denis']"
        caller.invokeMember('update_by_names').asString() == '3'
        caller.invokeMember('describe_dict').asString() == 'map:1'

        cleanup:
        ctx?.close()
    }

    void "a single abstract method interface of a library is a functional interface, so a lambda selects it over the Iterable overload"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.RepositoryCaller')).asPolyglotValue()

        expect: "the lambda implements Publisher.subscribe and receives the subscriber"
        caller.invokeMember('subscribe_publisher').asString() == 'publisher:from lambda'

        and: "a sequence still selects the Iterable overload"
        caller.invokeMember('subscribe_names').asString() == 'iterable:2'

        cleanup:
        ctx?.close()
    }

    void "custom functional interfaces of different arities are selected by the arity of the callable"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.RepositoryCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('find_one_predicate').asString() == 'Josh'
        caller.invokeMember('find_one_query').asString() == 'Denis'

        cleanup:
        ctx?.close()
    }

    void "a Python repository declaring specification overloads next to the inherited Iterable ones is called with lambdas"() {
        given:
        ApplicationContext ctx = buildContext(REPOSITORY_SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.RepositoryCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('count').asInt() == 2
        caller.invokeMember('find_one_predicate').asString() == 'Josh'
        caller.invokeMember('find_one_query').asString() == 'Denis by query'
        caller.invokeMember('update_by_specification').asInt() == 1
        caller.invokeMember('delete_by_specification').asInt() == 0
        caller.invokeMember('delete_by_names').asInt() == 2

        cleanup:
        ctx?.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanDefinitions(
            RuntimeBeanDefinition.builder(new SpecificationInterceptor())
                .singleton(true)
                .exposedTypes(SpecificationInterceptor.class, Interceptor.class)
                .build(),
            RuntimeBeanDefinition.builder(new SpecificationOverloads())
                .singleton(true)
                .build()
        )
    }
}
