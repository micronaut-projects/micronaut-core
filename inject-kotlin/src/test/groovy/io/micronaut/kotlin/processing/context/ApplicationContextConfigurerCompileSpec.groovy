package io.micronaut.kotlin.processing.context


import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class ApplicationContextConfigurerCompileSpec extends Specification {
    void "test compile context configurer"() {
        when:
        def context = buildContext('''
package test

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.context.ApplicationContextConfigurer
import io.micronaut.context.annotation.ContextConfigurer

@ContextConfigurer
class ExampleTest : ApplicationContextConfigurer {

}
''')

        then:
        context != null

    }
}
