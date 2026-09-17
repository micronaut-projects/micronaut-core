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

import io.micronaut.context.annotation.Primary
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.javatypes.DefaultImportedConnectionFactory
import io.micronaut.python.annotation.processing.test.javatypes.ImportedConnectionFactory
import io.micronaut.python.annotation.processing.test.javatypes.ImportedOptions
import io.micronaut.python.annotation.processing.test.javatypes.ImportedPooledConnection
import io.micronaut.python.annotation.processing.test.javatypes.ImportedServerBuilder
import io.micronaut.python.annotation.processing.test.javatypes.ImportedTagger
import org.graalvm.polyglot.Context
import some.pkg.async.AsyncContract
import some.pkg.async.AsyncThing

/**
 * Imported Java classes must behave like the host classes they stand for at run time: usable as
 * runtime type arguments of the bean context, in isinstance checks and as base classes, whether
 * they were imported, aliased with java.type or bound inside a try block.
 */
class ImportedJavaTypesRuntimeSpec extends AbstractPythonTypeElementSpec {

    void "imported Java interface and class are usable as runtime type arguments"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context import BeanContext
from micronaut.context.annotation import Import
from micronaut.python.annotation.processing.test.javatypes import DefaultImportedConnectionFactory, ImportedConnectionFactory, ImportedPooledConnection


@Import(classes=[DefaultImportedConnectionFactory, ImportedPooledConnection])
class Application:
    pass


@Singleton
class TypeProbe:
    def __init__(self, context: BeanContext):
        self.context = context

    def contains_factory(self) -> bool:
        return self.context.containsBean(ImportedConnectionFactory)

    def factory_count(self) -> int:
        return self.context.getBeansOfType(ImportedConnectionFactory).size()

    def factory_name(self) -> str:
        return self.context.getBean(ImportedConnectionFactory).name()

    def factory_is_instance(self) -> bool:
        return isinstance(self.context.getBean(ImportedConnectionFactory), ImportedConnectionFactory)

    def create_connection(self, url: str) -> str:
        connection = self.context.createBean(ImportedPooledConnection, url)
        if not isinstance(connection, ImportedPooledConnection):
            return "not an instance"
        return connection.url()

    def class_name(self) -> str:
        return ImportedConnectionFactory.class_.getName()
''', true)
        def probe = getBean(context, "python.TypeProbe").asPolyglotValue()

        expect:
        probe.invokeMember("contains_factory").asBoolean()
        probe.invokeMember("factory_count").asInt() == 1
        probe.invokeMember("factory_name").asString() == "default"
        probe.invokeMember("factory_is_instance").asBoolean()
        probe.invokeMember("create_connection", "jdbc:test").asString() == "jdbc:test"
        probe.invokeMember("class_name").asString() == ImportedConnectionFactory.name

        cleanup:
        context?.close()
    }

    void "imported Java classes are host classes in Python expressions"() {
        given:
        def context = buildContext('''
from micronaut.python.annotation.processing.test.javatypes import ImportedConnectionFactory, ImportedPooledConnection, DefaultImportedConnectionFactory


class Holder:
    pass
''', true)
        def polyglot = context.getBean(Context)

        expect:
        polyglot.eval("python", "isinstance(DefaultImportedConnectionFactory(), ImportedConnectionFactory)").asBoolean()
        polyglot.eval("python", "isinstance(ImportedPooledConnection('x'), ImportedPooledConnection)").asBoolean()
        !polyglot.eval("python", "isinstance(Holder(), ImportedPooledConnection)").asBoolean()
        polyglot.eval("python", "ImportedPooledConnection").as(Class) == ImportedPooledConnection
        polyglot.eval("python", "ImportedConnectionFactory").as(Class) == ImportedConnectionFactory

        and: "a class absent from the class path is bound to a facade that fails on first use"
        polyglot.eval("python", "from micronaut.python.annotation.processing.test.javatypes import _micronaut_java_type; type(_micronaut_java_type('missing.Type')).__name__").asString() == "_MicronautJavaType"
        polyglot.eval("python", "_micronaut_java_type('missing.Type')._target").asString() == "missing.Type"

        cleanup:
        context?.close()
    }

    void "java.type of a Micronaut annotation is usable as a qualifier stereotype"() {
        given:
        def context = buildContext('''
import java
from jakarta.inject import Singleton
from micronaut.context import BeanContext
from micronaut.context.annotation import Import
from micronaut.inject.qualifiers import Qualifiers
from micronaut.python.annotation.processing.test.javatypes import DefaultImportedConnectionFactory, ImportedConnectionFactory

Primary = java.type("io.micronaut.context.annotation.Primary")


@Import(classes=[DefaultImportedConnectionFactory])
class Application:
    pass


@Singleton
@Primary
class PrimaryFactory(ImportedConnectionFactory):
    def name(self) -> str:
        return "primary"


@Singleton
class QualifierProbe:
    def __init__(self, context: BeanContext):
        self.context = context

    def primary_name(self) -> str:
        return self.context.getBean(ImportedConnectionFactory, Qualifiers.byStereotype(Primary)).name()

    def stereotype_class(self) -> str:
        return Primary.java_class.getName()
''', true)
        def probe = getBean(context, "python.QualifierProbe").asPolyglotValue()

        expect:
        context.getBean(ImportedConnectionFactory, Qualifiers.byStereotype(Primary)).name() == "primary"
        probe.invokeMember("primary_name").asString() == "primary"
        probe.invokeMember("stereotype_class").asString() == Primary.name

        cleanup:
        context?.close()
    }

    void "a package with a Python keyword segment outside the Micronaut namespace can be imported"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.javatypes import ImportedOptions
from some.pkg.async_ import AsyncContract, AsyncThing


@Singleton
@ImportedOptions(taggers=[AsyncThing])
class AsyncProbe(AsyncContract):
    def describe(self) -> str:
        return AsyncThing.describe()
''', true)
        def polyglot = context.getBean(Context)

        expect:
        context.getBean(AsyncContract).describe() == "async thing"
        getBeanDefinition(context, "python.AsyncProbe").classValues(ImportedOptions, "taggers") == [AsyncThing] as Class[]
        polyglot.eval("python", "AsyncContract").as(Class) == AsyncContract
        polyglot.eval("python", "AsyncThing").as(Class) == AsyncThing

        cleanup:
        context?.close()
    }

    void "a java.type interface base supports constructor injection"() {
        given:
        def context = buildContext('''
import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Import
from micronaut.python.annotation.processing.test.javatypes import DefaultImportedConnectionFactory

ConnectionFactory = java.type("io.micronaut.python.annotation.processing.test.javatypes.ImportedConnectionFactory")


@Import(classes=[DefaultImportedConnectionFactory])
class Application:
    pass


@Singleton
class Dependency:
    def name(self) -> str:
        return "dependency"


@Singleton
class AliasedFactory(ConnectionFactory):
    def __init__(self, dependency: Dependency):
        self.dependency = dependency

    def name(self) -> str:
        return "aliased:" + self.dependency.name()


@Singleton
class InlineFactory(java.type("io.micronaut.python.annotation.processing.test.javatypes.ImportedConnectionFactory")):
    def __init__(self, dependency: Dependency):
        self.dependency = dependency

    def name(self) -> str:
        return "inline:" + self.dependency.name()
''', true)

        expect:
        context.getBeansOfType(ImportedConnectionFactory)*.name().sort() == ["aliased:dependency", "default", "inline:dependency"]

        cleanup:
        context?.close()
    }

    void "imports and java.type aliases bound in try blocks are resolved at compile time"() {
        given:
        def context = buildContext('''
import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Factory
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from micronaut.python.annotation.processing.test.javatypes import ImportedOptions

try:
    from micronaut.python.annotation.processing.test.javatypes import ImportedTagger
except ImportError:
    ImportedTagger = None

try:
    ServerBuilder = java.type("io.micronaut.python.annotation.processing.test.javatypes.ImportedServerBuilder$Builder")
except Exception:
    ServerBuilder = None


@Singleton
@ImportedOptions(taggers=[ImportedTagger])
class TaggedService:
    pass


@Factory
class BuilderFactory:
    @Bean
    def builder(self) -> ServerBuilder:
        return ServerBuilder()


@Singleton
class BuilderListener(BeanCreatedEventListener[ServerBuilder]):
    def onCreated(self, event: BeanCreatedEvent[ServerBuilder]) -> ServerBuilder:
        return event.getBean().name("customized")
''', true)

        when:
        def tagged = getBeanDefinition(context, "python.TaggedService")
        def listener = getBeanDefinition(context, "python.BuilderListener")

        then:
        tagged.classValues(ImportedOptions, "taggers") == [ImportedTagger] as Class[]
        listener.getTypeArguments(BeanCreatedEventListener)*.type == [ImportedServerBuilder.Builder]
        context.getBean(ImportedServerBuilder.Builder).name() == "customized"

        cleanup:
        context?.close()
    }
}
