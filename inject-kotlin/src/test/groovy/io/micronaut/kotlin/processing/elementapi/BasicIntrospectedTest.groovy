package io.micronaut.kotlin.processing.elementapi

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KspKt
import com.tschuchort.compiletesting.SourceFile
import io.micronaut.kotlin.processing.visitor.TypeElementSymbolProcessorProvider
import spock.lang.Specification

class BasicIntrospectedTest extends Specification {

    void "test basic introspection"() {
        when:
        Compiler.compile("test.Test","""
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {

}
""")

        then:
        noExceptionThrown()
    }
}
