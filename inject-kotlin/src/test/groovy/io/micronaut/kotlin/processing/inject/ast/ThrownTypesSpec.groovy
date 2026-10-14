package io.micronaut.kotlin.processing.inject.ast

import com.tschuchort.compiletesting.SourceFile
import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement

class ThrownTypesSpec extends AbstractKotlinCompilerSpec {

    void "test thrown types of Kotlin, Java source and compiled Java methods"() {
        given:
        Map<String, List<String>> thrown = [:]
        String genericPlaceholder = null
        def sources = [
                SourceFile.@Companion.java("test/MyEx.java", '''
package test;

public class MyEx extends Exception {
    public static class Nested extends Exception {
    }
}
''', true),
                SourceFile.@Companion.java("test/JavaBase.java", '''
package test;

public class JavaBase {
    public void javaThrows() throws java.io.IOException, MyEx.Nested {
    }

    public void javaNoThrows() {
    }

    public <E extends Exception> void javaGenericThrows() throws E {
    }
}
''', true),
                SourceFile.@Companion.kotlin("Test.kt", '''
package test

import java.io.IOException

abstract class Test : JavaBase(), AutoCloseable {

    @Throws(IOException::class, MyEx.Nested::class)
    fun kotlinThrows() {
    }

    fun kotlinNoThrows() {
    }

    @get:Throws(IllegalStateException::class)
    @set:Throws(IOException::class)
    var prop: String = ""

    abstract fun inputStream(): java.io.InputStream
}
''', true)
        ]

        when:
        KotlinCompiler.compile(sources, { ClassElement ce ->
            if (ce.name == 'test.Test') {
                ce.getEnclosedElements(ElementQuery.ALL_METHODS).each { MethodElement m ->
                    thrown[m.declaringType.simpleName + '.' + m.name] = m.thrownTypes*.name
                }
                def generic = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named('javaGenericThrows')).get().thrownTypes
                genericPlaceholder = generic.length == 1 && generic[0] instanceof GenericPlaceholderElement
                        ? ((GenericPlaceholderElement) generic[0]).variableName
                        : null
                ce.beanProperties.each { p ->
                    p.readMethod.ifPresent { m -> thrown['get.' + p.name] = m.thrownTypes*.name }
                    p.writeMethod.ifPresent { m -> thrown['set.' + p.name] = m.thrownTypes*.name }
                }
                def inputStream = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named('inputStream')).get().genericReturnType
                inputStream.getEnclosedElements(ElementQuery.ALL_METHODS.named('read')).each { MethodElement m ->
                    thrown['InputStream.read' + m.parameters.length] = m.thrownTypes*.name
                }
            }
        }, [])

        then: "the Kotlin @Throws classes are reported as before"
        thrown['Test.kotlinThrows'] == ['java.io.IOException', 'test.MyEx$Nested']
        thrown['Test.kotlinNoThrows'] == []
        thrown['Test.inputStream'] == []

        and: "the accessor @Throws classes are reported per accessor"
        thrown['get.prop'] == ['java.lang.IllegalStateException']
        thrown['set.prop'] == ['java.io.IOException']

        and: "the throws clause of a Java source method is reported"
        thrown['JavaBase.javaThrows'] == ['java.io.IOException', 'test.MyEx$Nested']
        thrown['JavaBase.javaNoThrows'] == []
        genericPlaceholder == 'E'

        and: "the throws clause of a compiled Java method is reported"
        thrown['AutoCloseable.close'] == ['java.lang.Exception']
        thrown['InputStream.read0'] == ['java.io.IOException']
        thrown['InputStream.read1'] == ['java.io.IOException']
        thrown['InputStream.read3'] == ['java.io.IOException']
    }
}
