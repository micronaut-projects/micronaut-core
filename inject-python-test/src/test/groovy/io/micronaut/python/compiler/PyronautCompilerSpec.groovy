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
import spock.lang.PendingFeature
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

    @PendingFeature(reason = "need to improve inheritance")
    def "test classpath support"() {
        given:
        def compiler = PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .classpath([new File("/tmp/fake.jar")])
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader != null
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
        def transformedFile = new File(tempDir, GraalPyContextFactory.APPLICATION_LAUNCHER_PATH)
        transformedFile.exists()

        def transformedContent = transformedFile.text
        // no transformation are applied
        pythonCode == transformedContent

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
from micronaut.context.annotation import Executable
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
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton

from .NotNull import NotNull

@Singleton
class NotNullExample:
    @NotNull
    @Executable
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

        cleanup:
        context.close()
        tempSrcDir.deleteDir()
    }
}
