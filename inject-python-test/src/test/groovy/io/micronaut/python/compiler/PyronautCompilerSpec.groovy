/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.GraalPyContextFactory
import io.micronaut.python.processing.PythonAnnotationProcessor
import spock.lang.Specification

class PyronautCompilerSpec extends Specification {

    def "test buildClassLoader with pythonCode"() {
        given:
        def pythonCode = '''
class TestClass:
    def hello(self):
        return "Hello from Python!"
'''
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader != null
        // Verify the generated main class can be loaded
        classLoader.loadClass("pyronaut_application.PyronautMain") != null
    }

    def "test buildClassLoader with pythonSrc"() {
        given:
        def tempDir = File.createTempDir("pyronaut-test-python", "")
        def pythonFile = new File(tempDir, "test.py")
        pythonFile.text = '''
class TestClass:
    def hello(self):
        return "Hello from Python!"
'''
        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempDir.absolutePath)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader != null
        // Verify the generated main class can be loaded
        classLoader.loadClass("pyronaut_application.PyronautMain") != null

        cleanup:
        tempDir.deleteDir()
    }

    def "test buildClassLoader uses explicit parent classloader"() {
        given:
        def parent = new ClassLoader(getClass().classLoader) {
        }
        def compiler = PyronautCompiler.builder()
            .pythonCode("class TestClass: pass")
            .parentClassLoader(parent)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader.parent.is(parent)
        classLoader.loadClass("pyronaut_application.PyronautMain") != null
    }

    def "test validation requires python source"() {
        when:
        PyronautCompiler.builder().build()

        then:
        thrown(IllegalArgumentException)
    }

    def "test package name validation"() {
        when:
        PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .packageName("invalid.package.name!")
            .build()

        then:
        thrown(IllegalArgumentException)
    }

    def "test compile to disk requires targetDir"() {
        given:
        def compiler = PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .build()

        when:
        compiler.compile()

        then:
        thrown(IllegalStateException)
    }

    def "test compile to disk"() {
        given:
        def targetDir = File.createTempDir("pyronaut-test", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .targetDir(targetDir)
            .build()

        when:
        compiler.compile()

        then:
        targetDir.exists()
        new File(targetDir, "pyronaut_application/PyronautMain.class").exists()

        cleanup:
        targetDir.deleteDir()
    }

    def "test compile to disk fails for invalid python source"() {
        given:
        def sourceDir = File.createTempDir("pyronaut-test-invalid-source", "")
        def targetDir = File.createTempDir("pyronaut-test-invalid-target", "")
        new File(sourceDir, "Broken.py").text = "class Broken("
        def compiler = PyronautCompiler.builder()
            .pythonSrc(sourceDir.absolutePath)
            .targetDir(targetDir)
            .build()

        when:
        compiler.compile()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Broken.py")
        e.message.contains("SyntaxError")

        cleanup:
        sourceDir.deleteDir()
        targetDir.deleteDir()
    }

    def "test Python exception subclass generates Throwable subtype"() {
        given:
        def pythonCode = '''
import java

RuntimeException = java.type("java.lang.RuntimeException")

class OutOfStockException(RuntimeException):
    pass
'''
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()
        def generatedException = classLoader.loadClass("python.OutOfStockException")

        then:
        RuntimeException.isAssignableFrom(generatedException)
    }

    def "test classpath support"() {
        given:
        def tempDir = File.createTempDir("pyronaut-test-classpath", "")
        def sourceDir = new File(tempDir, "src/example")
        def classesDir = new File(tempDir, "classes")
        sourceDir.mkdirs()
        classesDir.mkdirs()
        def externalSource = new File(sourceDir, "ExternalBase.java")
        externalSource.text = '''
package example;

public class ExternalBase {
    public String marker() {
        return "base";
    }
}
'''
        def javac = javax.tools.ToolProvider.systemJavaCompiler
        assert javac != null
        assert javac.run(null, null, null, "-d", classesDir.absolutePath, externalSource.absolutePath) == 0

        def compiler = PyronautCompiler.builder()
            .pythonCode('''
import java
from micronaut.context.annotation import Executable

ExternalBase = java.type("example.ExternalBase")

class Test:
    @Executable
    def make(self) -> ExternalBase:
        return ExternalBase()
''')
            .classpath([classesDir])
            .build()

        when:
        def classLoader = compiler.buildClassLoader()
        def generated = classLoader.loadClass("python.Test")

        then:
        classLoader != null
        generated.getDeclaredMethod("make").returnType.name == "example.ExternalBase"
        classLoader.loadClass("example.ExternalBase").name == "example.ExternalBase"

        cleanup:
        tempDir.deleteDir()
    }

    def "test annotation transformation and META-INF file generation"() {
        given:
        def pythonCode = '''
from micronaut.python.compiler import TestAnnotation, Singleton, Named

@TestAnnotation("test-value")
class MyService:
    def hello(self):
        return "Hello World"

@Singleton
class MySingletonService:
    pass

@Named("my-service")
class MyNamedService:
    pass
'''
        def tempDir = File.createTempDir("pyronaut-test-meta-inf", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        tempDir.exists()
        // Check that META-INF file was generated
        def transformedFile = new File(tempDir, GraalPyContextFactory.APPLICATION_SRC_PATH)
        transformedFile.exists()

        cleanup:
        tempDir.deleteDir()
    }

    def "test jakarta.inject annotation transformation"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton, Named

@Singleton
class MySingletonService:
    pass

@Named("my-service")
class MyNamedService:
    pass
'''
        def tempDir = File.createTempDir("pyronaut-test-jakarta", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        tempDir.exists()
        // Check that META-INF file was generated
        def metaInfDir = new File(tempDir, "META-INF")
        metaInfDir.exists()
        def transformedFile = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/jakarta/inject/Singleton.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text
        // Check that decorators were generated for jakarta.inject annotations
        transformedContent.contains("@micronaut_annotation(\"jakarta.inject.Singleton\")")
        transformedContent.contains("def Singleton(")

        cleanup:
        tempDir.deleteDir()
    }

    def "test generated JUnit stubs instantiate Python tests lazily"() {
        given:
        def pythonCode = '''
from org.junit.jupiter.api import Test

class MultipleTestSpec:
    @Test
    def first(self) -> None:
        pass

    @Test
    def second(self) -> None:
        pass
'''
        def tempDir = File.createTempDir("pyronaut-test-junit-stub", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def generated = new File(tempDir, "python/MultipleTestSpec.java")
        generated.exists()
        def javaCode = generated.text
        javaCode.contains('MultipleTestSpec() {\n  }')
        javaCode.contains('this.graalpyInternalValue = ContextHolder.newInstance("python", "MultipleTestSpec");')
        javaCode.contains('return this.graalpyInternalValue;')
        !javaCode.contains('MultipleTestSpec() {\n    this.graalpyInternalValue = ContextHolder.newInstance')

        cleanup:
        tempDir.deleteDir()
    }

    def "test repeatable annotation transformation"() {
        given:
        def pythonCode = '''
from micronaut.python.compiler import RepeatableAnnotation

@RepeatableAnnotation("first")
@RepeatableAnnotation("second")
class MyRepeatableService:
    pass
'''
        def tempDir = File.createTempDir("pyronaut-test-repeatable", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        tempDir.exists()
        // Check that META-INF file was generated
        def metaInfDir = new File(tempDir, "META-INF")
        metaInfDir.exists()
        def transformedFile = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/python/compiler/RepeatableAnnotation.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text

        // Check that decorator was generated with repeatable info using the new codepath
        transformedContent.contains("@micronaut_annotation(\"io.micronaut.python.compiler.RepeatableAnnotation\", repeated=\"io.micronaut.python.compiler.RepeatableAnnotations\")")
        transformedContent.contains("def RepeatableAnnotation(")

        cleanup:
        tempDir.deleteDir()
    }

    def "test transactional annotation transformation skips unavailable meta annotations"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from jakarta.transaction import Transactional

@Singleton
class MyTransactionalService:

    @Transactional
    def save(self):
        pass
'''
        def tempDir = File.createTempDir("pyronaut-test-transactional", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def metaInfDir = new File(tempDir, "META-INF")
        def transactionalFile = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/jakarta/transaction/Transactional.py")
        transactionalFile.exists()

        def transformedContent = transactionalFile.text
        transformedContent.contains("@micronaut_annotation(\"jakarta.transaction.Transactional\")")
        transformedContent.contains("def Transactional(")
        !transformedContent.contains("jakarta.interceptor")
        !transformedContent.contains("InterceptorBinding")

        cleanup:
        tempDir.deleteDir()
    }

    def "test Python keyword-safe Micronaut imports are normalized"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.async_.annotation import SingleResult
from micronaut.core.async_.propagation import ReactorPropagation
import java

Mono = java.type("reactor.core.publisher.Mono")

@Singleton
class AsyncImportService:
    @Executable
    @SingleResult
    def maybe(self) -> object:
        return Mono.empty()

    @Executable
    def propagation(self) -> object:
        return ReactorPropagation
'''
        def tempDir = File.createTempDir("pyronaut-test-keyword-import", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def metaInfDir = new File(tempDir, "META-INF")
        def singleResultFile = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/core/async_/annotation/SingleResult.py")
        singleResultFile.exists()
        singleResultFile.text.contains('@micronaut_annotation("io.micronaut.core.async.annotation.SingleResult")')

        def propagationInit = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/core/async_/propagation/__init__.py")
        propagationInit.exists()
        propagationInit.text.contains("ReactorPropagation = java.type('io.micronaut.core.async.propagation.ReactorPropagation')")

        def coreInit = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/core/__init__.py")
        coreInit.text.contains("from . import async_")

        cleanup:
        tempDir.deleteDir()
    }

    def "test Python keyword-safe Java method aliases are rewritten"() {
        given:
        def pythonCode = '''
from java.lang import Thread
from reactor.core.publisher import Mono
import java

ThreadAlias = java.type("java.lang.Thread")
FluxAlias = java.type("reactor.core.publisher.Flux")

class PythonKeywordMethod:
    def from_(self):
        return "python"

class KeywordMethodService:
    def imported(self):
        return Thread.yield_()

    def assigned(self):
        return ThreadAlias.yield_()

    def imported_reactor(self, publisher):
        return Mono.from_(publisher)

    def assigned_reactor(self, publisher):
        return FluxAlias.from_(publisher)

    def python_method(self):
        keyword = PythonKeywordMethod()
        return keyword.from_()
'''
        def tempDir = File.createTempDir("pyronaut-test-keyword-method", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def transformedFile = new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_LAUNCHER_PATH}")
        transformedFile.exists()
        def transformedContent = transformedFile.text
        transformedContent.contains("getattr(Thread, 'yield')()")
        transformedContent.contains("getattr(ThreadAlias, 'yield')()")
        transformedContent.contains("getattr(Mono, 'from')(publisher)")
        transformedContent.contains("getattr(FluxAlias, 'from')(publisher)")
        transformedContent.contains("keyword.from_()")
        !transformedContent.contains("Thread.yield_")
        !transformedContent.contains("ThreadAlias.yield_")
        !transformedContent.contains("Mono.from_")
        !transformedContent.contains("FluxAlias.from_")

        cleanup:
        tempDir.deleteDir()
    }

    def "test Python keyword-safe annotation members are rewritten"() {
        given:
        def pythonCode = '''
from micronaut.http.annotation import Controller, Error

@Controller("/errors")
class ErrorController:
    @Error(global_=True)
    def global_error(self) -> str:
        return "handled"
'''
        def tempDir = File.createTempDir("pyronaut-test-keyword-member", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def transformedFile = new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_LAUNCHER_PATH}")
        transformedFile.exists()
        def transformedContent = transformedFile.text
        transformedContent.contains("@Error(**{'global': True})")
        !transformedContent.contains("@Error(global_")

        cleanup:
        tempDir.deleteDir()
    }

    def "test nested annotation members are generated as Python attributes"() {
        given:
        def pythonCode = '''
from micronaut.context.annotation import Mapper
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Product:
    name: str

@Introspected
@dataclass
class ProductDTO:
    name: str

class ProductMappers(ABC):
    @Mapper.Mapping(to="name", **{"from": "product.name"})
    @abstractmethod
    def to_product_dto(self, product: Product) -> ProductDTO:
        pass

class MyMergeStrategy(Mapper.MergeStrategy):
    def merge(
        self,
        current_value: Any,
        value: Any,
        value_owner: Any,
        property_name: str,
        mapped_property_name: str,
    ) -> Any:
        return value
'''
        def tempDir = File.createTempDir("pyronaut-test-nested-annotation", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def mapperFile = new File(tempDir, "META-INF/" + PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/context/annotation/Mapper.py")
        mapperFile.exists()
        mapperFile.text.contains("def Mapping(")
        mapperFile.text.contains("Mapper.Mapping = Mapping")
        mapperFile.text.contains("Mapper.MergeStrategy = MergeStrategy")

        cleanup:
        tempDir.deleteDir()
    }

    def "test nested meta annotations are generated before parent annotation decorator"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable

@Serdeable
@dataclass
class Message:
    text: str
'''
        def tempDir = File.createTempDir("pyronaut-test-nested-meta-annotation", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def serdeableFile = new File(tempDir, "META-INF/" + PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/python/compiler/Serdeable.py")
        serdeableFile.exists()
        def transformedContent = serdeableFile.text
        transformedContent.indexOf("def Serializable(") < transformedContent.indexOf("@Serializable()")
        transformedContent.indexOf("def Deserializable(") < transformedContent.indexOf("@Deserializable()")
        transformedContent.indexOf("@Serializable()") < transformedContent.indexOf("def Serdeable(")
        transformedContent.indexOf("@Deserializable()") < transformedContent.indexOf("def Serdeable(")
        transformedContent.contains("@micronaut_annotation(\"io.micronaut.python.compiler.Serdeable\$Serializable\")")
        transformedContent.contains("@micronaut_annotation(\"io.micronaut.python.compiler.Serdeable\$Deserializable\")")
        transformedContent.contains("Serdeable.Serializable = Serializable")
        transformedContent.contains("Serdeable.Deserializable = Deserializable")

        cleanup:
        tempDir.deleteDir()
    }

    def "test generated introspected Python stubs expose public fields for host attribute access"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Message:
    text: str
'''
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()
        def messageClass = classLoader.loadClass("python.Message")

        then:
        messageClass.getField("text").type == String
    }

    def "test Python sources in multiple distinct packages are processed"() {
        given:
        def tempDir = File.createTempDir("pyronaut-test-multi-package", "")

        // Create directory structure with multiple packages
        def pythonDir = new File(tempDir, "")
        def exampleDir = new File(tempDir, "example")

        pythonDir.mkdirs()
        exampleDir.mkdirs()

        // Create Python files in different packages
        def helloControllerPy = new File(pythonDir, "HelloController.py")
        helloControllerPy.text = '''
from jakarta.inject import Singleton

@Singleton
class HelloController:
    def hello(self):
        return "Hello from Python package"
'''

        def userControllerPy = new File(exampleDir, "UserController.py")
        userControllerPy.text = '''
from jakarta.inject import Singleton

@Singleton
class UserController:
    def getUsers(self):
        return ["user1", "user2"]
'''

        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempDir.absolutePath)
            .javaSrc("inject-python-test/src/test/java")
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader != null

        when:
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider())
            .build()
            .start()

        then:
        context.getBean(classLoader.loadClass('example.UserController'))
        context.getBean(classLoader.loadClass('python.HelloController'))

        cleanup:
        context.close()
        tempDir.deleteDir()
    }

    def "test Python sources in multiple distinct packages are processed to disk"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-multi-package-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-multi-package-target", "")

        // Create directory structure with multiple packages
        def pythonDir = new File(tempSrcDir, "")
        def exampleDir = new File(tempSrcDir, "example")

        pythonDir.mkdirs()
        exampleDir.mkdirs()

        // Create Python files in different packages
        def helloControllerPy = new File(pythonDir, "HelloController.py")
        helloControllerPy.text = '''
from jakarta.inject import Singleton
from example import UserController

@Singleton
class HelloController:
    def __init__(self, dependency: UserController):
        self.dependency = dependency

    def hello(self):
        return "Hello from Python package"
'''

        def userControllerPy = new File(exampleDir, "UserController.py")
        userControllerPy.text = '''
from jakarta.inject import Singleton
@Singleton
class UserController:
    def getUsers(self):
        return ["user1", "user2"]
'''

        def compiler = PyronautCompiler.builder()
                .pythonSrc(tempSrcDir.absolutePath)
                .javaSrc("inject-python-test/src/test/java")
                .targetDir(tempTargetDir)
                .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())

        then:
        classLoader != null

        when:
        def context = ApplicationContext.builder()
                .classLoader(classLoader)
                .build()
                .start()

        then:
        context.getBean(classLoader.loadClass('example.UserController'))
        context.getBean(classLoader.loadClass('python.HelloController'))

        cleanup:
        context.close()
        tempSrcDir.deleteDir()
    }

    def "test Python sources in multiple distinct packages are processed to disk - relative import"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-multi-package-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-multi-package-target", "")

        // Create directory structure with multiple packages
        def pythonDir = new File(tempSrcDir, "example")
        def exampleDir = new File(tempSrcDir, "example")

        pythonDir.mkdirs()
        exampleDir.mkdirs()

        // Create Python files in different packages
        def helloControllerPy = new File(pythonDir, "HelloController.py")
        helloControllerPy.text = '''
from jakarta.inject import Singleton
from .UserController import UserController

@Singleton
class HelloController:
    def __init__(self, dependency: UserController):
        self.dependency = dependency

    def hello(self):
        return "Hello from Python package"
'''

        def userControllerPy = new File(exampleDir, "UserController.py")
        userControllerPy.text = '''
from jakarta.inject import Singleton
@Singleton
class UserController:
    def getUsers(self):
        return ["user1", "user2"]
'''

        def compiler = PyronautCompiler.builder()
                .pythonSrc(tempSrcDir.absolutePath)
                .javaSrc("inject-python-test/src/test/java")
                .targetDir(tempTargetDir)
                .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())

        then:
        classLoader != null

        when:
        def context = ApplicationContext.builder()
                .classLoader(classLoader)
                .build()
                .start()

        then:
        context.getBean(classLoader.loadClass('example.UserController'))
        context.getBean(classLoader.loadClass('example.HelloController'))

        cleanup:
        context.close()
        tempSrcDir.deleteDir()
    }

    def "test relative submodule import resolves generic repository entity"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-data-repository-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-data-repository-target", "")

        def appDir = new File(tempSrcDir, "example/micronaut")
        def domainDir = new File(appDir, "domain")
        domainDir.mkdirs()

        new File(domainDir, "genre.py").text = '''
from dataclasses import dataclass
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from typing import Annotated

@dataclass
@MappedEntity
class Genre:
    id: Annotated[int | None, Id, GeneratedValue]
    name: str
'''

        new File(appDir, "genre_repository.py").text = '''
from jakarta.data.repository import Save
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository
from typing import List

from .domain.genre import Genre

@JdbcRepository(dialect = "H2")
class GenreRepository(CrudRepository[Genre, int]):

    @Save
    def saveGenre(self, genre: Genre) -> None: ...

    def findAll(self) -> List[Genre]: ...
'''

        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempSrcDir.absolutePath)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())

        then:
        classLoader.loadClass('example.micronaut.GenreRepository') != null

        cleanup:
        tempSrcDir.deleteDir()
        tempTargetDir.deleteDir()
    }

    def "test data repository join on nullable collection relation compiles"() {
        given:
        def tempTargetDir = File.createTempDir("pyronaut-test-data-join-target", "")
        def pythonCode = '''
from dataclasses import dataclass, field
from typing import Annotated, Optional

from jakarta.validation.constraints import NotNull
from micronaut.core.annotation import NonNull, Nullable
from micronaut.data.annotation import GeneratedValue, Id, Join, MappedEntity, Relation
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository

@dataclass
@MappedEntity
class Message:
    content: str
    room: Annotated["Room | None", Nullable, Relation(value="MANY_TO_ONE")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None

@dataclass
@MappedEntity
class Room:
    name: str
    messages: Annotated[
        list[Message] | None,
        Nullable,
        Relation(value="ONE_TO_MANY", mappedBy="room"),
    ] = field(default_factory=list)
    id: Annotated[int | None, Id, GeneratedValue] = None

@JdbcRepository(dialect="H2")
class RoomRepository(CrudRepository[Room, int]):
    @Join(value="messages", type=Join.Type.LEFT_FETCH)
    def getById(self, id: Annotated[int, NonNull, NotNull]) -> Optional[Room]: ...
'''

        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())

        then:
        classLoader.loadClass('python.RoomRepository') != null

        cleanup:
        tempTargetDir.deleteDir()
    }

    def "test file-backed classless route with execute on resolves script proxy type"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-script-route-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-script-route-target", "")

        def appDir = new File(tempSrcDir, "example/micronaut")
        appDir.mkdirs()

        new File(appDir, "genre_controller.py").text = '''
from micronaut.http.annotation import Delete, Get
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn

@ExecuteOn(TaskExecutors.BLOCKING)
@Get(value="/genres/{id}", produces="text/plain")
def show(id: int) -> str:
    return str(id)

@Delete("/genres/{id}")
def delete(id: int) -> None:
    pass
'''

        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempSrcDir.absolutePath)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())

        then:
        classLoader.loadClass('example.micronaut.Genre_controller') != null

        cleanup:
        tempSrcDir.deleteDir()
        tempTargetDir.deleteDir()
    }

    def "test relative import of Python decorator"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-multi-package-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-multi-package-target", "")

        // Create directory structure with multiple packages
        def pythonDir = new File(tempSrcDir, "example")
        def exampleDir = new File(tempSrcDir, "example")

        pythonDir.mkdirs()
        exampleDir.mkdirs()

        // Create Python files in different packages
        def notNullPy = new File(pythonDir, "NotNull.py")
        notNullPy.text = '''
from micronaut.aop import Around
@Around
def NotNull(func):
    return func
'''

        def notNullInterceptor = new File(exampleDir, "NotNullInterceptor.py")
        notNullInterceptor.text = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext
import java
from .NotNull import NotNull

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(NotNull)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        for param in context.getParameters().values():
            if (param.getValue() is None):
                raise Exception(f"Null parameter [{param.getName()}] is not allowed")
        return context.proceed()
'''

        def notNullExample = new File(exampleDir, "NotNullExample.py")
        notNullExample.text = '''
from jakarta.inject import Singleton

from .NotNull import NotNull

@Singleton
class NotNullExample:
    @NotNull
    def doWork(self, taskName : str):
        print(f"Doing job: {taskName}")
'''

        def compiler = PyronautCompiler.builder()
                .pythonSrc(tempSrcDir.absolutePath)
                .javaSrc("inject-python-test/src/test/java")
                .targetDir(tempTargetDir)
                .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())

        then:
        classLoader != null

        when:
        def context = ApplicationContext.builder()
                .classLoader(classLoader)
                .build()
                .start()

        def bean = context.getBean(classLoader.loadClass('example.NotNullExample'))
        bean.doWork(null)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Exception: Null parameter [taskName] is not allowed'

        when:
        bean.asPolyglotValue().invokeMember("doWork", [null] as Object[])

        then:
        e = thrown(RuntimeException)
        e.message == 'Exception: Null parameter [taskName] is not allowed'

        cleanup:
        context.close()
        tempSrcDir.deleteDir()
    }
}
