package io.micronaut.python.compiler

import spock.lang.Specification

/**
 * Plain Python helper classes in an application's sources compile into valid Java, or into no Java at
 * all when nothing in Java can use them (micronaut-projects/pyronaut#331).
 */
class PlainHelperClassSpec extends Specification {

    void "a helper method returning or accepting a Python exception class compiles"() {
        given:
        def tempDir = File.createTempDir("pyronaut-plain-helper", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode('''
class ConditionError(ValueError):
    pass


class Evaluator:
    def error(self, message: str) -> ConditionError:
        return ConditionError(message)

    def describe(self, error: ConditionError) -> str:
        return str(error)
''')
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        new File(tempDir, "python/ConditionError.java").text.contains("public static ConditionError fromPolyglotValue(Value")
        new File(tempDir, "python/Evaluator.java").text.contains("ConditionError.fromPolyglotValue(")

        cleanup:
        tempDir.deleteDir()
    }

    void "a private helper class with no decorator gets no Java type"() {
        given:
        def sourceDir = File.createTempDir("pyronaut-private-helper-src", "")
        def packageDir = new File(sourceDir, "weather_agent/core")
        packageDir.mkdirs()
        new File(packageDir, "condition.py").text = '''
from jakarta.inject import Singleton


class ConditionError(ValueError):
    pass


class _Evaluator:
    def error(self, message: str) -> ConditionError:
        return ConditionError(message)


@Singleton
class Service:
    def evaluator(self) -> _Evaluator:
        return _Evaluator()
'''
        def tempDir = File.createTempDir("pyronaut-private-helper", "")
        def compiler = PyronautCompiler.builder()
            .pythonSrc(sourceDir.absolutePath)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        !new File(tempDir, "weather_agent/core/_Evaluator.java").exists()
        new File(tempDir, "weather_agent/core/Service.java").exists()
        !new File(tempDir, "weather_agent/core/Service.java").text.contains("_Evaluator")

        cleanup:
        tempDir.deleteDir()
        sourceDir.deleteDir()
    }

    void "classes named by the exclude option get no Java type and still run as Python"() {
        given:
        def tempDir = File.createTempDir("pyronaut-excluded-helper", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode('''
from jakarta.inject import Singleton


class InternalError(ValueError):
    pass


class InternalHelper:
    def error(self, message: str) -> InternalError:
        return InternalError(message)


@Singleton
class Service:
    def helper(self) -> InternalHelper:
        return InternalHelper()
''')
            .options(["-Amicronaut.python.exclude=python.Internal*"])
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def pythonSources = []
        tempDir.eachFileRecurse { file ->
            if (file.name.endsWith(".py")) {
                pythonSources << file.text
            }
        }

        then:
        !new File(tempDir, "python/InternalHelper.java").exists()
        !new File(tempDir, "python/InternalError.java").exists()
        new File(tempDir, "python/Service.java").exists()
        !new File(tempDir, "python/Service.java").text.contains("InternalHelper")
        pythonSources.any { it.contains("class InternalHelper") }

        cleanup:
        tempDir.deleteDir()
    }
}
