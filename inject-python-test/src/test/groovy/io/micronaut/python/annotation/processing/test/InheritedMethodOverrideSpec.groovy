package io.micronaut.python.annotation.processing.test

import io.micronaut.context.annotation.Executable
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.DeleteOneInterceptor
import io.micronaut.data.intercept.FindOptionalInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.python.annotation.processing.test.inherited.BoxedIdRepository
import io.micronaut.python.annotation.processing.test.inherited.DefaultMethodGreeter
import io.micronaut.python.annotation.processing.test.inherited.InheritedMethodCaller

import java.lang.reflect.Method

/**
 * A Python method that overrides an inherited Java interface method (abstract or default) must be bridged with
 * the inherited Java signature (boxed {@code Integer} when the interface says so) and kept in the bean definition,
 * whether or not it is annotated with {@code @Executable}.
 */
class InheritedMethodOverrideSpec extends AbstractPythonTypeElementSpec {

    void "test python override of a default interface method is bridged without @Executable"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
import java

DefaultMethodGreeter = java.type("io.micronaut.python.annotation.processing.test.inherited.DefaultMethodGreeter")


@Singleton
class PythonGreeter(DefaultMethodGreeter):

    def __init__(self):
        self.events = []

    def name(self) -> str:
        return "python"

    def greet(self, who: str) -> str:
        return "python:" + who

    def onEvent(self, event: str) -> None:
        self.events.append(event)

    def getOrder(self) -> int:
        return 42
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.PythonGreeter")
        Class<?> stub = bean.getClass()

        then: "the default methods overridden in Python are declared on the generated stub"
        bean instanceof DefaultMethodGreeter
        stub.getDeclaredMethod("greet", String).returnType == String
        stub.getDeclaredMethod("onEvent", String).returnType == void
        stub.getDeclaredMethod("getOrder").returnType == int

        and: "a Java caller reaches the Python body through the interface"
        InheritedMethodCaller.greet(bean, "world") == "python:world"
        InheritedMethodCaller.order(bean) == 42

        when:
        InheritedMethodCaller.onEvent(bean, "started")

        then:
        bean.asPolyglotValue().getMember("events").getArraySize() == 1
        bean.asPolyglotValue().getMember("events").getArrayElement(0).asString() == "started"

        cleanup:
        context?.close()
    }

    void "test an unannotated python override of a default interface method is bridged by name and arity"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
import java

DefaultMethodGreeter = java.type("io.micronaut.python.annotation.processing.test.inherited.DefaultMethodGreeter")


@Singleton
class LooseGreeter(DefaultMethodGreeter):

    def name(self):
        return "python"

    def greet(self, who):
        return "python:" + who

    def onEvent(self, *events):
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.LooseGreeter")
        Class<?> stub = bean.getClass()

        then: "the overrides without type hints are declared on the stub with the Java signature"
        stub.getDeclaredMethod("greet", String).returnType == String
        stub.getDeclaredMethod("onEvent", String).returnType == void
        stub.declaredMethods.every { Method m -> m.name != "getOrder" }

        and: "a Java caller reaches the Python body through the interface"
        InheritedMethodCaller.greet(bean, "world") == "python:world"
        InheritedMethodCaller.order(bean) == 0

        when:
        InheritedMethodCaller.onEvent(bean, "started")

        then:
        noExceptionThrown()

        cleanup:
        context?.close()
    }

    void "test default interface methods that are not overridden in python are not bridged"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
import java

DefaultMethodGreeter = java.type("io.micronaut.python.annotation.processing.test.inherited.DefaultMethodGreeter")


@Singleton
class DefaultGreeter(DefaultMethodGreeter):

    def name(self) -> str:
        return "python"
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.DefaultGreeter")
        Class<?> stub = bean.getClass()

        then: "the default implementation keeps running"
        stub.declaredMethods.every { Method m -> !(m.name in ["greet", "onEvent", "getOrder"]) }
        InheritedMethodCaller.greet(bean, "world") == "default:world"
        InheritedMethodCaller.order(bean) == 0

        cleanup:
        context?.close()
    }

    void "test python override of an inherited Integer typed method uses the inherited signature (#hint)"() {
        given:
        def pythonCode = """
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
import java

BoxedIdRepository = java.type("io.micronaut.python.annotation.processing.test.inherited.BoxedIdRepository")
Optional = java.type("java.util.Optional")


@dataclass
class Person:
    id: int
    name: str


@Singleton
class PersonRepository(BoxedIdRepository[Person, int]):

    def __init__(self):
        self.deleted = []

    def findById(self, id: $hint) -> Optional[Person]:
        return Optional.of(Person(id, "person-" + str(id)))

    @Executable
    def deleteById(self, id: $hint) -> None:
        self.deleted.append(id)

    def count(self) -> int:
        return len(self.deleted)
"""

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.PersonRepository")
        def definition = getBeanDefinition(context, "python.PersonRepository")
        Class<?> stub = bean.getClass()
        List<Method> findByIdMethods = stub.declaredMethods.findAll { it.name == "findById" && !it.synthetic }
        List<Method> deleteByIdMethods = stub.declaredMethods.findAll { it.name == "deleteById" && !it.synthetic }

        then: "a single bridge with the boxed inherited parameter type is generated"
        bean instanceof BoxedIdRepository
        findByIdMethods.size() == 1
        findByIdMethods[0].parameterTypes == [Integer] as Class[]
        deleteByIdMethods.size() == 1
        deleteByIdMethods[0].parameterTypes == [Integer] as Class[]

        and: "the bean definition keeps the python declared override with its annotations and the inherited signature"
        definition.executableMethods.count { it.methodName == "deleteById" } == 1
        definition.executableMethods.find { it.methodName == "deleteById" }.arguments[0].type == Integer
        definition.executableMethods.find { it.methodName == "deleteById" }.hasAnnotation(Executable)

        and: "a Java caller passing an Integer reaches the Python body"
        InheritedMethodCaller.findById(bean, 7).get().name == "person-7"

        when:
        InheritedMethodCaller.deleteById(bean, 3)

        then:
        bean.count() == 1L

        where:
        hint << ["int", "int | None"]
    }

    void "test python override of inherited abstract Integer method is kept in an introduction bean definition"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
BoxedIdRepository = java.type("io.micronaut.python.annotation.processing.test.inherited.BoxedIdRepository")
Optional = java.type("java.util.Optional")


@dataclass
class Person:
    id: int
    name: str


@Introduction
def RepoIntro(cls):
    return cls


@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.getExecutableMethod().getName() + ":" + str(context.getParameterValues()[0]) if len(context.getParameterValues()) > 0 else None


@RepoIntro
class PersonRepository(BoxedIdRepository[Person, int]):

    @Executable(processOnStartup=True)
    def deleteById(self, id: int | None) -> None: ...

    def findById(self, id: int) -> Optional[Person]: ...
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.PersonRepository")
        def deleteById = definition.executableMethods.findAll { it.methodName == "deleteById" }
        def findById = definition.executableMethods.findAll { it.methodName == "findById" }

        then: "the python declared override replaces the inherited method and keeps its own annotations"
        deleteById.size() == 1
        deleteById[0].arguments[0].type == Integer
        deleteById[0].booleanValue(Executable, "processOnStartup").orElse(false)
        findById.size() == 1
        findById[0].arguments[0].type == Integer

        cleanup:
        context?.close()
    }

    void "test micronaut data repository python override of inherited CrudRepository methods keeps the python metadata (#hint)"() {
        given:
        def pythonCode = """
from dataclasses import dataclass
from typing import Annotated
import java

from micronaut.data.annotation import GeneratedValue, Id, Join, MappedEntity, Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository

Optional = java.type("java.util.Optional")


@dataclass
@MappedEntity
class Manufacturer:
    id: Annotated[int | None, Id, GeneratedValue]
    name: str


@dataclass
@MappedEntity
class Product:
    id: Annotated[int | None, Id, GeneratedValue]
    name: str
    manufacturer: Manufacturer


@JdbcRepository(dialect="H2")
class ProductRepository(CrudRepository[Product, int]):

    @Query("UPDATE product SET enabled = false WHERE id = :id")
    def deleteById(self, id: $hint) -> None: ...

    @Join("manufacturer")
    def findById(self, id: $hint) -> Optional[Product]: ...
"""

        when:
        def definition = buildBeanDefinition("python", "ProductRepository\$RuntimeProxy", pythonCode)
        def deleteById = definition.executableMethods.findAll { it.methodName == "deleteById" }
        def findById = definition.executableMethods.findAll { it.methodName == "findById" }
        def runtimeDeleteById = definition.beanType.methods.findAll { it.name == "deleteById" && !it.synthetic }
        def runtimeFindById = definition.beanType.methods.findAll { it.name == "findById" && !it.synthetic }

        then: "the python override replaces the inherited method and keeps its custom query"
        deleteById.size() == 1
        deleteById[0].arguments[0].type == Integer
        deleteById[0].stringValue(Query).get() == "UPDATE product SET enabled = false WHERE id = :id"
        deleteById[0].stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() != DeleteOneInterceptor.name

        and: "the python override of findById keeps its join and the inherited Integer signature"
        findById.size() == 1
        findById[0].arguments[0].type == Integer
        findById[0].stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == FindOptionalInterceptor.name
        findById[0].getAnnotationValuesByType(Join)*.stringValue().collect { it.get() } == ["manufacturer"]

        and: "the generated class declares one bridge per method with the boxed inherited type"
        runtimeDeleteById*.parameterTypes.flatten() == [Integer]
        runtimeFindById*.parameterTypes.flatten() == [Integer]

        where:
        hint << ["int", "int | None"]
    }
}
