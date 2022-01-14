package io.micronaut.kotlin.processing.beans

import io.micronaut.kotlin.processing.KotlinCompiler
import spock.lang.Specification

class SingletonSpec extends Specification {

    void "test simple singleton bean"() {
        when:
        def definition = KotlinCompiler.buildBeanDefinition("test.Test", """
package test

 import jakarta.inject.Singleton

@Singleton
class Test
""")

        then:
        noExceptionThrown()
        definition != null
    }
}
