package io.micronaut.python.annotation.processing.test

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import io.micronaut.python.annotation.processing.test.repository.MinimalCrudRepository
import io.micronaut.python.compiler.PyronautCompiler
import org.graalvm.polyglot.Value
import spock.lang.PendingFeature

class IntroductionGenericInterfaceSpec extends AbstractPythonTypeElementSpec {

    void "test introduction interfaces declared by annotation are implemented by python class"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from micronaut.websocket.annotation import ClientWebSocket

@ClientWebSocket("/chat/{topic}/{username}")
class ChatClient(ABC):
    @abstractmethod
    def send(self, message: str) -> None:
        pass
'''

        expect:
        def definition = buildBeanDefinition("python", "ChatClient\$RuntimeProxy", pythonCode)
        definition != null
        definition.executableMethods*.methodName.contains("setWebSocketSession")
    }

    void "test introduction bean inheriting generic Java repository interface compiles"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
from typing import List
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
MinimalCrudRepository = java.type("io.micronaut.python.annotation.processing.test.repository.MinimalCrudRepository")

@dataclass
class MyPerson:
    id: int
    name: str

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        raise RuntimeError("repo introduction interceptor executed")

@RepoIntro
class MyPersonRepository(MinimalCrudRepository[MyPerson, int], new_style=True):
    def savePerson(self, person: MyPerson) -> None:
        pass

    def findAll(self) -> List[MyPerson]:
        pass

    def findAllById(self, id: int) -> MyPerson:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.MyPersonRepository")
        Value bean = getBean(context, "python.MyPersonRepository").asPolyglotValue()


        then:
        !definition.getTypeArguments(MinimalCrudRepository).isEmpty()
        bean != null

        when:
        bean.invokeMember("save", [null] as Object[])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("repo introduction interceptor executed")

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0066")
    void "test introduction generic return and argument types from Java interface"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
MinimalCrudRepository = java.type("io.micronaut.python.annotation.processing.test.repository.MinimalCrudRepository")

@dataclass
class MyPerson:
    id: int
    name: str

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

@RepoIntro
class MyPersonRepository(MinimalCrudRepository[MyPerson, int], new_style=True):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.MyPersonRepository")
        def save = definition.executableMethods.find { it.methodName == "save" }
        def findById = definition.executableMethods.find { it.methodName == "findById" }
        def findAll = definition.executableMethods.find { it.methodName == "findAll" }

        then:
        save.returnType.type.name == "python.MyPerson"
        save.arguments[0].type.name == "python.MyPerson"
        findById.arguments[0].type == Integer.TYPE
        findById.returnType.type == Optional
        findById.returnType.typeVariables["T"].type.name == "python.MyPerson"
        findAll.returnType.type == Iterable
        findAll.returnType.typeVariables["T"].type.name == "python.MyPerson"

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0064")
    void "test introduction generic return and argument types from base class"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Generic, List, TypeVar
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
T = TypeVar("T")

@dataclass
class Person:
    name: str

@dataclass
class SubPerson(Person):
    age: int

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

class MyInterface(Generic[T], ABC):
    @abstractmethod
    def get_person(self) -> T:
        pass

    @abstractmethod
    def get_people(self) -> List[T]:
        pass

    @abstractmethod
    def save(self, person: T) -> None:
        pass

    @abstractmethod
    def save_all(self, people: List[T]) -> None:
        pass

@RepoIntro
@Singleton
@Executable
class MyBean(MyInterface[SubPerson], ABC):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.MyBean")
        Value bean = getBean(context, "python.MyBean").asPolyglotValue()
        def getPerson = definition.executableMethods.find { it.methodName == "get_person" }
        def getPeople = definition.executableMethods.find { it.methodName == "get_people" }
        def save = definition.executableMethods.find { it.methodName == "save" }
        def saveAll = definition.executableMethods.find { it.methodName == "save_all" }

        then:
        !definition.isAbstract()
        definition.injectedFields.size() == 0
        definition.executableMethods.size() == 4
        getPerson.returnType.type.name == "python.SubPerson"
        getPeople.returnType.type == List
        getPeople.returnType.asArgument().hasTypeVariables()
        getPeople.returnType.asArgument().typeVariables["E"].type.name == "python.SubPerson"
        save.arguments[0].type.name == "python.SubPerson"
        saveAll.arguments[0].type == List
        saveAll.arguments[0].typeVariables["E"].type.name == "python.SubPerson"
        bean.invokeMember("get_person").isNull()
        bean.invokeMember("get_people").isNull()
        bean.invokeMember("save", [null] as Object[]).isNull()

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0065")
    void "test introduction bounded generic return types without concrete type argument"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Generic, List, TypeVar
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@dataclass
class Person:
    name: str

@dataclass
class SubPerson(Person):
    age: int

T = TypeVar("T", bound=Person)

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

class MyInterface(Generic[T], ABC):
    @abstractmethod
    def get_person(self) -> T:
        pass

    @abstractmethod
    def get_people(self) -> List[T]:
        pass

@RepoIntro
@Singleton
@Executable
class MyBean(MyInterface, ABC):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.MyBean")
        Value bean = getBean(context, "python.MyBean").asPolyglotValue()
        def getPerson = definition.executableMethods.find { it.methodName == "get_person" }
        def getPeople = definition.executableMethods.find { it.methodName == "get_people" }

        then:
        getPerson.returnType.type.name == "python.Person"
        getPeople.returnType.type == List
        getPeople.returnType.asArgument().typeVariables["E"].type.name == "python.Person"
        bean.invokeMember("get_person").isNull()
        bean.invokeMember("get_people").isNull()

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0063")
    void "test introduction generic type argument annotations propagate to methods"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from typing import Annotated
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
from jakarta.validation import Valid
from jakarta.validation.constraints import Min
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
DataCrudRepository = java.type("io.micronaut.python.annotation.processing.test.repository.DataCrudRepository")

@dataclass
class MyPerson:
    id: int
    name: str

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

@RepoIntro
class MyPersonRepository(DataCrudRepository[Annotated[MyPerson, Valid], Annotated[int, Min(5)]], new_style=True):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.MyPersonRepository")
        def save = definition.executableMethods.find { it.methodName == "save" }
        def findById = definition.executableMethods.find { it.methodName == "findById" }

        then:
        save != null
        findById != null
        save.arguments[0].annotationMetadata.hasAnnotation(Valid)
        findById.arguments[0].annotationMetadata.hasAnnotation(Min)
        findById.arguments[0].annotationMetadata.getValue(Min, Integer).get() == 5
        findById.returnType.annotationMetadata.hasAnnotation(Valid)

        cleanup:
        context?.close()
    }

    void "test introduction advice unwraps Python entity wrappers from result shapes"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from abc import ABC, abstractmethod
import java

Array = java.type("java.lang.reflect.Array")
ArrayList = java.type("java.util.ArrayList")
Collections = java.type("java.util.Collections")
CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
LinkedHashSet = java.type("java.util.LinkedHashSet")
Optional = java.type("java.util.Optional")
Publishers = java.type("io.micronaut.core.async.publisher.Publishers")
Stream = java.type("java.util.stream.Stream")

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
Subscriber = java.type("org.reactivestreams.Subscriber")

@dataclass(frozen=True)
class MyPerson:
    id: int
    name: str

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def boxed(self, name: str):
        person_type = java.type("python.MyPerson")
        return person_type.fromPolyglotValue(MyPerson(1, name))

    def list_of(self, person):
        values = ArrayList()
        values.add(person)
        return values

    def intercept(self, context: MethodInvocationContext):
        method_name = context.getMethodName()
        person = self.boxed(method_name)
        if method_name == "findOne":
            return person
        if method_name == "findList":
            return self.list_of(person)
        if method_name == "findSet":
            values = LinkedHashSet()
            values.add(person)
            return values
        if method_name == "findIterable":
            return Collections.unmodifiableCollection(self.list_of(person))
        if method_name == "findOptional":
            return Optional.of(person)
        if method_name == "findArray":
            person_type = java.type("python.MyPerson")
            values = Array.newInstance(person_type, 1)
            Array.set(values, 0, person)
            return values
        if method_name == "findStream":
            return Stream.of(person)
        if method_name == "findAsync":
            return CompletableFuture.completedFuture(person)
        if method_name == "findPublisher":
            return Publishers.just(person)
        raise RuntimeError("Unexpected method " + method_name)

@RepoIntro
class MyPersonRepository(ABC):
    @abstractmethod
    def findOne(self) -> object:
        pass

    @abstractmethod
    def findList(self) -> object:
        pass

    @abstractmethod
    def findSet(self) -> object:
        pass

    @abstractmethod
    def findIterable(self) -> object:
        pass

    @abstractmethod
    def findOptional(self) -> object:
        pass

    @abstractmethod
    def findArray(self) -> object:
        pass

    @abstractmethod
    def findStream(self) -> object:
        pass

    @abstractmethod
    def findAsync(self) -> object:
        pass

    @abstractmethod
    def findPublisher(self) -> object:
        pass

class FirstSubscriber(Subscriber):
    def __init__(self):
        self.future = CompletableFuture()

    def onSubscribe(self, subscription):
        subscription.request(1)

    def onNext(self, item):
        self.future.complete(item)

    def onError(self, error):
        self.future.completeExceptionally(error)

    def onComplete(self):
        pass

@Singleton
class ResultShapeCaller:
    def __init__(self, repository: MyPersonRepository):
        self.repository = repository

    def assert_person(self, person, source: str) -> str:
        try:
            return person.name
        except Exception as exc:
            raise RuntimeError(source + " returned " + str(type(person)) + " without readable name: " + str(exc))

    @Executable
    def one_name(self) -> str:
        return self.assert_person(self.repository.findOne(), "findOne")

    @Executable
    def list_name(self) -> str:
        return self.assert_person(self.repository.findList()[0], "findList")

    @Executable
    def set_name(self) -> str:
        for person in self.repository.findSet():
            return self.assert_person(person, "findSet")
        raise RuntimeError("findSet returned no results")

    @Executable
    def iterable_name(self) -> str:
        for person in self.repository.findIterable():
            return self.assert_person(person, "findIterable")
        raise RuntimeError("findIterable returned no results")

    @Executable
    def optional_name(self) -> str:
        return self.assert_person(self.repository.findOptional().get(), "findOptional")

    @Executable
    def array_name(self) -> str:
        return self.assert_person(self.repository.findArray()[0], "findArray")

    @Executable
    def stream_name(self) -> str:
        return self.assert_person(self.repository.findStream().findFirst().get(), "findStream")

    @Executable
    def async_name(self) -> str:
        return self.assert_person(self.repository.findAsync().toCompletableFuture().get(), "findAsync")

    @Executable
    def publisher_name(self) -> str:
        subscriber = FirstSubscriber()
        self.repository.findPublisher().subscribe(subscriber)
        return self.assert_person(subscriber.future.get(), "findPublisher")
'''

        when:
        def context = buildContext(pythonCode)
        def caller = getBean(context, "python.ResultShapeCaller").asPolyglotValue()

        then:
        caller.invokeMember("one_name").asString() == "findOne"
        caller.invokeMember("list_name").asString() == "findList"
        caller.invokeMember("set_name").asString() == "findSet"
        caller.invokeMember("iterable_name").asString() == "findIterable"
        caller.invokeMember("optional_name").asString() == "findOptional"
        caller.invokeMember("array_name").asString() == "findArray"
        caller.invokeMember("stream_name").asString() == "findStream"
        caller.invokeMember("async_name").asString() == "findAsync"
        caller.invokeMember("publisher_name").asString() == "findPublisher"

        cleanup:
        context?.close()
    }

    void "test introduction advice supports Python protocol methods"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from typing import Protocol
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@dataclass(frozen=True)
class MyPerson:
    id: int
    name: str

@Introduction
def RepoIntro(cls):
    return cls

@InterceptorBean(RepoIntro)
@Singleton
class RepoIntroInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        method_name = context.getMethodName()
        if method_name == "findName":
            return "protocol-name"
        if method_name == "save":
            return context.getParameterValues()[0]
        raise RuntimeError("Unexpected method " + method_name)

@RepoIntro
class MyPersonRepository(Protocol):
    def findName(self, id: int) -> str:
        ...

    def save(self, person: MyPerson) -> MyPerson:
        ...

@Singleton
class ProtocolCaller:
    def __init__(self, repository: MyPersonRepository):
        self.repository = repository

    @Executable
    def find_name(self) -> str:
        return self.repository.findName(1)

    @Executable
    def save_name(self) -> str:
        saved = self.repository.save(MyPerson(1, "Denis"))
        try:
            return saved.name
        except Exception as exc:
            raise RuntimeError("Protocol introduction returned " + str(type(saved)) + " without readable name: " + str(exc))
'''

        when:
        def context = buildContext(pythonCode)
        def repository = getBean(context, "python.MyPersonRepository").asPolyglotValue()
        def caller = getBean(context, "python.ProtocolCaller").asPolyglotValue()

        then:
        repository.invokeMember("findName", 1).asString() == "protocol-name"
        caller.invokeMember("find_name").asString() == "protocol-name"
        caller.invokeMember("save_name").asString() == "Denis"

        cleanup:
        context?.close()
    }

    void "test micronaut data CrudRepository generic inheritance compiles"() {
        given:
        def pythonCode = '''
import java
Dialect = java.type("io.micronaut.data.model.query.builder.sql.Dialect")

from dataclasses import dataclass
from micronaut.data.annotation import MappedEntity
from micronaut.data.annotation import Id
from typing import Annotated
from typing import List
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository
from abc import ABC, abstractmethod
from jakarta.data.repository import Save

@dataclass
@MappedEntity
class MyPerson:
    id : Annotated[int, Id]
    name : str
    age : int

@JdbcRepository(dialect = "H2")
class MyPersonRepository(ABC, CrudRepository[MyPerson, int]):

    @Save
    @abstractmethod
    def savePerson(self, person : MyPerson) -> None:
        pass

    @abstractmethod
    def findAll(self) -> List[MyPerson]:
        pass

    @abstractmethod
    def findAllById(self, id: int) -> MyPerson:
        pass
'''

        when:
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .build()

        def classLoader = compiler.buildClassLoader()
        def repositoryType = classLoader.loadClass("python.MyPersonRepository")

        then:
        repositoryType != null
    }
}
