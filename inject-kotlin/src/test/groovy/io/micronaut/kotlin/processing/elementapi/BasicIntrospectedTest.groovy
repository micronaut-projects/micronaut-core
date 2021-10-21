package io.micronaut.kotlin.processing.elementapi

import spock.lang.Specification

class BasicIntrospectedTest extends Specification {

    void "test basic introspection"() {
        when:
        def introspection = Compiler.buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {

}
""")

        then:
        noExceptionThrown()
        introspection != null
        introspection.instantiate().class.name == "test.Test"
    }
}
