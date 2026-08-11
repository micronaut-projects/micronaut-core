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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.model.Association
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.python.processing.PythonAnnotationProcessor
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
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

    def "test file-backed VFS sources preserve exact content across relative imports"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-original-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-original-target", "")
        def packageDir = new File(tempSrcDir, "example")
        packageDir.mkdirs()
        def serviceCode = '''# debugger-visible comment
from .helper import answer


class Service:
    def value(self):
        return answer(  21  )  # preserve spacing
'''
        def helperCode = '''def answer(value):
    return value * 2
'''
        new File(packageDir, "service.py").text = serviceCode
        new File(packageDir, "helper.py").text = helperCode
        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempSrcDir.absolutePath)
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()

        then:
        new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}example/service.py").text == serviceCode
        new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}example/helper.py").text == helperCode

        cleanup:
        tempSrcDir.deleteDir()
        tempTargetDir.deleteDir()
    }

    def "test multiple Python source roots preserve exact source and remain loadable"() {
        given:
        def firstRoot = File.createTempDir("pyronaut-test-first-root", "")
        def secondRoot = File.createTempDir("pyronaut-test-second-root", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-multiple-roots-target", "")
        def alphaDir = new File(firstRoot, "alpha")
        def betaDir = new File(secondRoot, "beta")
        alphaDir.mkdirs()
        betaDir.mkdirs()
        def alphaCode = '''# first source root
from jakarta.inject import Singleton

@Singleton
class AlphaService:
    def value(self) -> str:
        return "alpha"
'''
        def betaCode = '''# second source root
from jakarta.inject import Singleton

@Singleton
class BetaService:
    def value(self) -> str:
        return "beta"
'''
        new File(alphaDir, "AlphaService.py").text = alphaCode
        new File(betaDir, "BetaService.py").text = betaCode
        def compiler = PyronautCompiler.builder()
            .pythonSrc("${firstRoot.absolutePath},${secondRoot.absolutePath}")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .build()
            .start()

        then:
        new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}alpha/AlphaService.py").text == alphaCode
        new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}beta/BetaService.py").text == betaCode
        context.getBean(classLoader.loadClass('alpha.AlphaService')) != null
        context.getBean(classLoader.loadClass('beta.BetaService')) != null

        cleanup:
        context?.close()
        classLoader?.close()
        firstRoot.deleteDir()
        secondRoot.deleteDir()
        tempTargetDir.deleteDir()
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
        generatedException.getConstructor(Value) != null
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
        javaCode.contains('static final PythonContextRuntime.PythonClassReference __PYTHON_CLASS_REFERENCE =')
        javaCode.contains('"class-instance:python.MultipleTestSpec"')
        javaCode.contains('this.graalpyInternalValue = PythonContextRuntime.newInstance(MultipleTestSpec.__PYTHON_CLASS_REFERENCE);')
        javaCode.contains('return this.graalpyInternalValue;')
        !javaCode.contains('MultipleTestSpec() {\n    this.graalpyInternalValue = PythonContextRuntime.newInstance')

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

    def "test nested repeatable annotation transformation"() {
        given:
        def pythonCode = '''
from micronaut.python.compiler import NestedRepeatableAnnotation

@NestedRepeatableAnnotation("first")
@NestedRepeatableAnnotation("second")
class MyNestedRepeatableService:
    pass
'''
        def tempDir = File.createTempDir("pyronaut-test-nested-repeatable", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def metaInfDir = new File(tempDir, "META-INF")
        def transformedFile = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/python/compiler/NestedRepeatableAnnotation.py")
        transformedFile.exists()
        def transformedContent = transformedFile.text
        transformedContent.contains('@micronaut_annotation("io.micronaut.python.compiler.NestedRepeatableAnnotation", repeated="io.micronaut.python.compiler.NestedRepeatableAnnotation.List")')
        transformedContent.contains("def NestedRepeatableAnnotation(")
        !transformedContent.contains("@List()")

        def packageInit = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/python/compiler/__init__.py")
        packageInit.exists()
        packageInit.text.contains("from .NestedRepeatableAnnotation import NestedRepeatableAnnotation")

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
        singleResultFile.text.contains('@micronaut_annotation("io.micronaut.core.async.annotation.SingleResult"')

        def propagationInit = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/core/async_/propagation/__init__.py")
        propagationInit.exists()
        propagationInit.text.contains("ReactorPropagation = java.type('io.micronaut.core.async.propagation.ReactorPropagation')")

        def coreInit = new File(metaInfDir, PythonAnnotationProcessor.APPLICATION_SRC_PATH + "/micronaut/core/__init__.py")
        coreInit.text.contains("from . import async_")

        cleanup:
        tempDir.deleteDir()
    }

    def "test imported Java keyword methods use generated facades and direct aliases use mapped bytecode"() {
        given:
        def pythonCode = '''
from java.lang import Thread
from reactor.core.publisher import Mono as ImportedMono
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
        return ImportedMono.from_(publisher)

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
        def classLoader = new URLClassLoader(tempDir.toURI().toURL())
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .build()
            .start()
        def pythonContext = context.getBean(org.graalvm.polyglot.Context)

        then:
        def sourceFile = new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_LAUNCHER_PATH}")
        sourceFile.exists()
        sourceFile.text == pythonCode
        new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}java/lang/__init__.py")
            .text.contains("Thread = _MicronautJavaType(java.type('java.lang.Thread'), False)")
        new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}reactor/core/publisher/__init__.py")
            .text.contains("Mono = _MicronautJavaType(java.type('reactor.core.publisher.Mono'), False)")
        new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}__pycache__")
            .listFiles().any { it.name.startsWith('__main__.') && it.name.endsWith('.pyc') }
        pythonContext.eval("python", "KeywordMethodService().imported_reactor(ImportedMono.just('imported')).block()").asString() == 'imported'
        pythonContext.eval("python", "KeywordMethodService().assigned_reactor(ImportedMono.just('assigned')).blockFirst()").asString() == 'assigned'
        pythonContext.eval("python", "KeywordMethodService().python_method()").asString() == 'python'

        cleanup:
        context?.close()
        classLoader?.close()
        tempDir.deleteDir()
    }

    def "test Python keyword-safe annotation members keep original runtime source"() {
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
        def sourceFile = new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_LAUNCHER_PATH}")
        sourceFile.exists()
        sourceFile.text == pythonCode
        new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}micronaut/http/annotation/Error.py")
            .text.contains('@micronaut_annotation("io.micronaut.http.annotation.Error"')

        cleanup:
        tempDir.deleteDir()
    }

    def "test mapped runtime bytecode preserves original traceback locations with optional bytecode #compileBytecode"() {
        given:
        def pythonCode = '''from jakarta.inject import Singleton
import java

Mono = java.type("reactor.core.publisher.Mono")

@Singleton
class DebugService:
    pass

def debug_failure():
    Mono.from_(Mono.just("ready")).block()
    raise RuntimeError("debug boom")
'''
        def tempDir = File.createTempDir("pyronaut-test-traceback", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .compilePythonBytecode(compileBytecode)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempDir.toURI().toURL())
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .build()
            .start()
        def pythonContext = context.getBean(org.graalvm.polyglot.Context)
        pythonContext.eval("python", "debug_failure()")

        then:
        def error = thrown(PolyglotException)
        error.message.contains("debug boom")
        def applicationFrame = error.polyglotStackTrace.find { it.rootName == 'debug_failure' }
        applicationFrame != null
        applicationFrame.sourceLocation.startLine == 12
        applicationFrame.sourceLocation.source.name == '/graalpy_vfs/src/__main__.py'
        pythonContext.eval("python", "'getattr' in debug_failure.__code__.co_names and 'from_' not in debug_failure.__code__.co_names").asBoolean()
        new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_LAUNCHER_PATH}").text == pythonCode
        def cacheFile = new File(tempDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}__pycache__")
            .listFiles().find { it.name.startsWith('__main__.') && it.name.endsWith('.pyc') }
        cacheFile != null
        java.nio.ByteBuffer.wrap(cacheFile.bytes, 4, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .getInt() == 3

        cleanup:
        context?.close()
        classLoader?.close()
        tempDir.deleteDir()

        where:
        compileBytecode << [false, true]
    }

    def "test file-backed mapped runtime bytecode preserves original traceback locations with optional bytecode #compileBytecode"() {
        given:
        def pythonCode = '''# directory-backed debugger source
from jakarta.inject import Singleton
import java

Mono = java.type("reactor.core.publisher.Mono")

@Singleton
class DebugService:
    pass

def debug_failure():
    Mono.from_(Mono.just("ready")).block()
    raise RuntimeError("directory debug boom")
'''
        def tempSrcDir = File.createTempDir("pyronaut-test-directory-traceback-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-directory-traceback-target", "")
        def packageDir = new File(tempSrcDir, "example")
        packageDir.mkdirs()
        new File(packageDir, "debug_service.py").text = pythonCode
        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempSrcDir.absolutePath)
            .targetDir(tempTargetDir)
            .compilePythonBytecode(compileBytecode)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .build()
            .start()
        def pythonContext = context.getBean(org.graalvm.polyglot.Context)
        pythonContext.eval("python", "from example.debug_service import debug_failure; debug_failure()")

        then:
        def error = thrown(PolyglotException)
        error.message.contains("directory debug boom")
        def applicationFrame = error.polyglotStackTrace.find { it.rootName == 'debug_failure' }
        applicationFrame != null
        applicationFrame.sourceLocation.startLine == 13
        applicationFrame.sourceLocation.source.name == '/graalpy_vfs/src/example/debug_service.py'
        pythonContext.eval("python", "'getattr' in debug_failure.__code__.co_names and 'from_' not in debug_failure.__code__.co_names").asBoolean()
        def moduleCache = pythonContext.eval(
            "python",
            "__import__('example.debug_service', fromlist=['']).__cached__"
        ).asString()
        moduleCache.contains('/example/__pycache__/debug_service.')
        moduleCache.endsWith('.pyc')
        new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}example/debug_service.py").text == pythonCode

        cleanup:
        context?.close()
        classLoader?.close()
        tempSrcDir.deleteDir()
        tempTargetDir.deleteDir()

        where:
        compileBytecode << [false, true]
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
        mapperFile.text.contains("def _Mapper_Mapping(")
        !mapperFile.text.contains("def Mapping(")
        mapperFile.text.contains("Mapper.Mapping = _Mapper_Mapping")
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
        transformedContent.indexOf("def _Serdeable_Serializable(") < transformedContent.indexOf("@_Serdeable_Serializable()")
        transformedContent.indexOf("def _Serdeable_Deserializable(") < transformedContent.indexOf("@_Serdeable_Deserializable()")
        transformedContent.indexOf("@_Serdeable_Serializable()") < transformedContent.indexOf("def Serdeable(")
        transformedContent.indexOf("@_Serdeable_Deserializable()") < transformedContent.indexOf("def Serdeable(")
        transformedContent.contains("@micronaut_annotation(\"io.micronaut.python.compiler.Serdeable\$Serializable\")")
        transformedContent.contains("@micronaut_annotation(\"io.micronaut.python.compiler.Serdeable\$Deserializable\")")
        !transformedContent.contains("def Serializable(")
        !transformedContent.contains("def Deserializable(")
        transformedContent.contains("Serdeable.Serializable = _Serdeable_Serializable")
        transformedContent.contains("Serdeable.Deserializable = _Serdeable_Deserializable")

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

    def "test generated introspected Python stubs expose declared instance methods"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Message:
    text: str

    def formattedDateCreated(self) -> str:
        return "formatted: " + self.text
'''
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider())
            .build()
            .start()
        def messageClass = classLoader.loadClass("python.Message")
        def message = messageClass.getConstructor(String).newInstance("today")

        then:
        messageClass.getMethod("formattedDateCreated").returnType == String
        message.formattedDateCreated() == "formatted: today"

        cleanup:
        context?.close()
    }

    def "test bytecode-enabled in-memory compiler transforms annotated application and decorator modules"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Message:
    text: str

    def formattedDateCreated(self) -> str:
        return "formatted: " + self.text
'''
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .compilePythonBytecode(true)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()
        def filesList = classLoader.getResourceAsStream("META-INF/GRAALPY-VFS/micronaut-application/fileslist.txt").text
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider())
            .build()
            .start()
        def messageClass = classLoader.loadClass("python.Message")
        def message = messageClass.getConstructor(String).newInstance("today")

        then:
        filesList.contains("/src/__main__.py")
        filesList.contains("/src/__pycache__/__main__.")
        filesList.contains("/src/micronaut/core/annotation/Introspected.py")
        filesList.contains("/src/micronaut/core/annotation/__pycache__/Introspected.")
        filesList.contains("/src/micronaut/core/__pycache__/__init__.")
        messageClass.getMethod("formattedDateCreated").returnType == String
        message.formattedDateCreated() == "formatted: today"

        cleanup:
        context?.close()
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

    def "test bytecode-enabled disk compiler loads annotated relative-import application from cache"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-bytecode-relative-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-bytecode-relative-target", "")
        def exampleDir = new File(tempSrcDir, "example")
        exampleDir.mkdirs()

        new File(exampleDir, "HelloController.py").text = '''
from jakarta.inject import Singleton
from .UserController import UserController

@Singleton
class HelloController:
    def __init__(self, dependency: UserController):
        self.dependency = dependency

    def hello(self):
        return self.dependency.getUsers()[0]
'''
        new File(exampleDir, "UserController.py").text = '''
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
            .compilePythonBytecode(true)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())
        def filesList = new File(tempTargetDir, "META-INF/GRAALPY-VFS/micronaut-application/fileslist.txt").text
        def context = ApplicationContext.builder()
            .classLoader(classLoader)
            .build()
            .start()
        def pythonContext = context.getBean(org.graalvm.polyglot.Context)
        def cachedModule = pythonContext.eval("python", "import importlib; importlib.import_module('example.HelloController').__cached__").asString()

        then:
        filesList.contains("/src/example/HelloController.py")
        filesList.contains("/src/example/__pycache__/HelloController.")
        filesList.contains("/src/example/__init__.py")
        filesList.contains("/src/example/__pycache__/__init__.")
        filesList.contains("/src/jakarta/inject/Singleton.py")
        filesList.contains("/src/jakarta/inject/__pycache__/Singleton.")
        context.getBean(classLoader.loadClass('example.UserController'))
        context.getBean(classLoader.loadClass('example.HelloController'))
        cachedModule.contains("/__pycache__/HelloController.")
        cachedModule.endsWith(".pyc")

        cleanup:
        context?.close()
        tempSrcDir.deleteDir()
        tempTargetDir.deleteDir()
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

    def "test data repository join on nullable collection relation compiles with #optionalImport"() {
        given:
        def tempTargetDir = File.createTempDir("pyronaut-test-data-join-target", "")
        def pythonCode = """
from dataclasses import dataclass, field
from typing import Annotated
${optionalImport}

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
    def getById(self, id: Annotated[int, NonNull, NotNull]) -> ${optionalType}[Room]: ...
"""

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

        where:
        optionalImport                 | optionalType
        "from typing import Optional"  | "Optional"
        "from java.util import Optional" | "Optional"
    }

    def "test imported annotation exposes nested enum members"() {
        given:
        def tempTargetDir = File.createTempDir("pyronaut-test-annotation-nested-enum", "")
        def pythonCode = '''
from micronaut.data.annotation import Join

@Join(value="messages", type=Join.Type.LEFT_FETCH)
class RoomRepository:
    pass
'''

        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()

        then:
        def joinFile = new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}/micronaut/data/annotation/Join.py")
        joinFile.exists()
        def joinContent = joinFile.text
        joinContent.contains('Type = java.type("io.micronaut.data.annotation.Join$Type")')
        joinContent.contains("Join.Type = Type")

        and:
        def launcherFile = new File(tempTargetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_LAUNCHER_PATH}")
        launcherFile.exists()
        launcherFile.text.contains('type=Join.Type.LEFT_FETCH')

        cleanup:
        tempTargetDir.deleteDir()
    }

    def "test file-backed data repository join with relative entity imports compiles"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-data-join-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-data-join-target", "")

        def entitiesDir = new File(tempSrcDir, "example/micronaut/entities")
        def repositoriesDir = new File(tempSrcDir, "example/micronaut/repositories")
        entitiesDir.mkdirs()
        repositoriesDir.mkdirs()

        new File(entitiesDir, "message.py").text = '''
from dataclasses import dataclass
from typing import TYPE_CHECKING, Annotated

from micronaut.core.annotation import Nullable
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

if TYPE_CHECKING:
    from .room import Room

@dataclass
@MappedEntity
class Message:
    content: str
    room: Annotated["Room | None", Nullable, Relation(value="MANY_TO_ONE")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None
'''

        new File(entitiesDir, "room.py").text = '''
from dataclasses import dataclass, field
from typing import Annotated

from micronaut.core.annotation import Nullable
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

from .message import Message

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
'''

        new File(repositoriesDir, "room_repository.py").text = '''
from typing import Annotated

from jakarta.validation.constraints import NotNull
from java.util import Optional
from micronaut.core.annotation import NonNull
from micronaut.data.annotation import Join
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository

from ..entities.room import Room

@JdbcRepository(dialect="H2")
class RoomRepository(CrudRepository[Room, int]):
    @Join(value="messages", type=Join.Type.LEFT_FETCH)
    def getById(self, id: Annotated[int, NonNull, NotNull]) -> Optional[Room]: ...
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
        classLoader.loadClass('example.micronaut.repositories.RoomRepository') != null
        def roomSource = new File(tempTargetDir, "example/micronaut/entities/Room.java").text
        roomSource.contains("public List<Message> messages;")
        roomSource.contains("public void setMessages(List<Message> arg1)")
        roomSource.contains("GraalPyRuntimeUtil.convertList(arg1, (element) -> Message.fromPolyglotValue(element))")

        cleanup:
        tempSrcDir.deleteDir()
        tempTargetDir.deleteDir()
    }

    def "test inherited data repository methods keep subclass-specific metadata"() {
        given:
        def tempTargetDir = File.createTempDir("pyronaut-test-data-repository-metadata-target", "")
        def pythonCode = '''
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository

@dataclass
@MappedEntity
class ContactEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    firstName: str
    lastName: str

@dataclass
@MappedEntity
class PhoneEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    phone: str

@JdbcRepository(dialect="H2")
class ContactRepository(CrudRepository[ContactEntity, int]):
    pass

@JdbcRepository(dialect="H2")
class PhoneRepository(CrudRepository[PhoneEntity, int]):
    pass
'''

        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())
        def contactDefinition = classLoader.loadClass('python.$ContactRepository$RuntimeProxy$Definition').newInstance()
        def phoneDefinition = classLoader.loadClass('python.$PhoneRepository$RuntimeProxy$Definition').newInstance()
        def contactSave = contactDefinition.executableMethods.find { it.methodName == "save" && it.arguments.length == 1 }
        def phoneSave = phoneDefinition.executableMethods.find { it.methodName == "save" && it.arguments.length == 1 }

        then:
        contactSave != null
        phoneSave != null
        contactSave.arguments[0].type.name == "python.ContactEntity"
        phoneSave.arguments[0].type.name == "python.PhoneEntity"
        contactSave.classValue(DataMethod, DataMethod.META_MEMBER_ROOT_ENTITY).get().name == "python.ContactEntity"
        phoneSave.classValue(DataMethod, DataMethod.META_MEMBER_ROOT_ENTITY).get().name == "python.PhoneEntity"

        cleanup:
        tempTargetDir.deleteDir()
    }

    def "test file backed association property keeps entity generic when projection uses same property name"() {
        given:
        def tempSrcDir = File.createTempDir("pyronaut-test-association-generic-src", "")
        def tempTargetDir = File.createTempDir("pyronaut-test-association-generic-target", "")

        def appDir = new File(tempSrcDir, "example/micronaut")
        appDir.mkdirs()

        new File(appDir, "contact_entity.py").text = '''
from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

if TYPE_CHECKING:
    from .phone_entity import PhoneEntity

@dataclass
@MappedEntity("contact")
class ContactEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    firstName: str
    lastName: str
    phones: Annotated[
        list[PhoneEntity],
        Relation(value=Relation.Kind.ONE_TO_MANY, mappedBy="contact"),
    ] = field(default_factory=list)
'''

        new File(appDir, "phone_entity.py").text = '''
from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

if TYPE_CHECKING:
    from .contact_entity import ContactEntity

@dataclass
@MappedEntity("phone")
class PhoneEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    phone: str
    contact: Annotated[ContactEntity, Relation(value=Relation.Kind.MANY_TO_ONE)]
'''

        new File(appDir, "contact_complete.py").text = '''
from dataclasses import dataclass

from micronaut.core.annotation import Introspected

@dataclass
@Introspected
class ContactComplete:
    id: int | None
    firstName: str
    lastName: str
    phones: list[str] | None = None
'''

        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempSrcDir.absolutePath)
            .javaSrc("inject-python-test/src/test/java")
            .targetDir(tempTargetDir)
            .build()

        when:
        compiler.compile()
        def classLoader = new URLClassLoader(tempTargetDir.toURI().toURL())
        BeanIntrospection introspection = classLoader.loadClass('example.micronaut.$ContactEntity$Introspection').newInstance() as BeanIntrospection
        def phones = introspection.getRequiredProperty("phones", List)
        def phoneArgument = phones.asArgument().getFirstTypeVariable().orElse(null)
        BeanIntrospection completeIntrospection = classLoader.loadClass('example.micronaut.$ContactComplete$Introspection').newInstance() as BeanIntrospection
        def completePhones = completeIntrospection.getRequiredProperty("phones", List)
        def completePhoneArgument = completePhones.asArgument().getFirstTypeVariable().orElse(null)
        def entity = new RuntimePersistentEntity(introspection) {
            @Override
            protected RuntimePersistentEntity getEntity(Class type) {
                BeanIntrospection associatedIntrospection = classLoader.loadClass("${type.packageName}.\$${type.simpleName}\$Introspection").newInstance() as BeanIntrospection
                return new RuntimePersistentEntity(associatedIntrospection)
            }
        }
        def association = entity.getPropertyByName("phones") as Association

        then:
        phoneArgument != null
        phoneArgument.type.name == "example.micronaut.PhoneEntity"
        completePhoneArgument != null
        completePhoneArgument.type.name == "java.lang.String"
        completePhones.asArgument().isNullable()
        association.associatedEntity.name == "example.micronaut.PhoneEntity"

        cleanup:
        tempSrcDir.deleteDir()
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
