package io.micronaut.kotlin.processing.elementapi

import spock.lang.Specification

class BeanIntrospectionSpec extends Specification {

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

    void "test generics in arrays don't stack overflow"() {
        given:
        def introspection = Compiler.buildBeanIntrospection('arraygenerics.Test', '''
package arraygenerics

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable 

@Introspected
class Test<T : CharSequence> {

    lateinit var array: Array<T>
    lateinit var starArray: Array<*>
    lateinit var stringArray: Array<String>
    
    @Executable
    fun myMethod(): Array<T> = array
}
''')
        expect:
        introspection.getRequiredProperty("array", CharSequence[].class)
                .type == CharSequence[].class
        introspection.beanMethods.first().returnType.type == CharSequence[].class
    }
}
