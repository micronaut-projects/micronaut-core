package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler
import spock.lang.PendingFeature

class IntroductionGenericInterfaceSpec extends AbstractPythonTypeElementSpec {

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
        return None

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
        def bean = getBean(context, "python.MyPersonRepository")

        then:
        bean != null

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
