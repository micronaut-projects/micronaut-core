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

    def "test custom package name"() {
        given:
        def compiler = PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .packageName("com.example.test")
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader.loadClass("com.example.test.PyronautMain") != null
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
from io.micronaut.python.compiler import TestAnnotation, Singleton, Named

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
        def metaInfDir = new File(tempDir, "META-INF")
        metaInfDir.exists()
        def transformedFile = new File(metaInfDir, "pyronaut_application.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text
        transformedContent.contains("@TestAnnotation")
        transformedContent.contains("@Singleton")
        transformedContent.contains("@Named")
        transformedContent.contains("class MyService:")
        transformedContent.contains("class MySingletonService:")
        transformedContent.contains("class MyNamedService:")

        // Check that decorators were generated
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.TestAnnotation')")
        transformedContent.contains("def TestAnnotation(")
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.Singleton')")
        transformedContent.contains("def Singleton(")
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.Named')")
        transformedContent.contains("def Named(")

        // Check for meta-annotations (Singleton should also generate Scope decorator)
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.Scope')")
        transformedContent.contains("def Scope(")

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
        def transformedFile = new File(metaInfDir, "pyronaut_application.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text
        transformedContent.contains("@Singleton")
        transformedContent.contains("@Named")
        transformedContent.contains("class MySingletonService:")
        transformedContent.contains("class MyNamedService:")

        // Check that decorators were generated for jakarta.inject annotations
        transformedContent.contains("@micronaut_annotation('jakarta.inject.Singleton')")
        transformedContent.contains("def Singleton(")
        transformedContent.contains("@micronaut_annotation('jakarta.inject.Named')")
        transformedContent.contains("def Named(")

        cleanup:
        tempDir.deleteDir()
    }

    def "test repeatable annotation transformation"() {
        given:
        def pythonCode = '''
from io.micronaut.python.compiler import RepeatableAnnotation

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
        def transformedFile = new File(metaInfDir, "pyronaut_application.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text
        transformedContent.contains("@RepeatableAnnotation")
        transformedContent.contains("class MyRepeatableService:")

        // Check that decorator was generated with repeatable info using the new codepath
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.RepeatableAnnotation', repeated='io.micronaut.python.compiler.RepeatableAnnotations')")
        transformedContent.contains("def RepeatableAnnotation(")

        cleanup:
        tempDir.deleteDir()
    }

    def "test nested annotation transformation"() {
        given:
        def pythonCode = '''
from io.micronaut.python.compiler import IntrospectedAnnotation, BuilderAnnotation

@IntrospectedAnnotation(builder=BuilderAnnotation(style="custom"))
class MyIntrospectedService:
    pass
'''
        def tempDir = File.createTempDir("pyronaut-test-nested", "")
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
        def transformedFile = new File(metaInfDir, "pyronaut_application.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text
        !transformedContent.contains("from io.micronaut.python.compiler import IntrospectedAnnotation, BuilderAnnotation")
        transformedContent.contains("@IntrospectedAnnotation(builder=BuilderAnnotation(style='custom'))")
        transformedContent.contains("class MyIntrospectedService:")

        // Check that main decorator was generated
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.IntrospectedAnnotation')")
        transformedContent.contains("def IntrospectedAnnotation(")

        // Check that nested BuilderAnnotation decorator was generated by inspecting annotation methods
        transformedContent.contains("@micronaut_annotation('io.micronaut.python.compiler.BuilderAnnotation')")
        transformedContent.contains("def BuilderAnnotation(")

        cleanup:
        tempDir.deleteDir()
    }

    def "test real micronaut nested annotation transformation"() {
        given:
        def pythonCode = '''
from io.micronaut.core.annotation import Introspected

@Introspected
class MyRealIntrospectedService:
    pass
'''
        def tempDir = File.createTempDir("pyronaut-test-real-nested", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .javaSrc("core/src/main/java")
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        tempDir.exists()
        // Check that META-INF file was generated
        def metaInfDir = new File(tempDir, "META-INF")
        metaInfDir.exists()
        def transformedFile = new File(metaInfDir, "pyronaut_application.py")
        transformedFile.exists()

        // Verify the transformed content contains the original code and generated decorators
        def transformedContent = transformedFile.text
        !transformedContent.contains("from io.micronaut.core.annotation import Introspected")
        transformedContent.contains("@Introspected")
        transformedContent.contains("class MyRealIntrospectedService:")

        // Check that main decorator was generated
        transformedContent.contains("@micronaut_annotation('io.micronaut.core.annotation.Introspected')")
        transformedContent.contains("def Introspected(")

        // Check that nested IntrospectionBuilder decorator was generated by inspecting annotation methods
        transformedContent.contains("@micronaut_annotation('io.micronaut.core.annotation.Introspected.IntrospectionBuilder')")
        transformedContent.contains("def IntrospectionBuilder(")

        cleanup:
        tempDir.deleteDir()
    }
}
