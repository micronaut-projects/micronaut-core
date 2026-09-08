package io.micronaut.kotlin.processing.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext

class PreDestroyOnBeanClassSpec extends AbstractKotlinCompilerSpec {

    void "test preDestroy declared on the bean class"() {
        given:
        ApplicationContext context = buildContext('''
package test

import io.micronaut.context.annotation.Bean
import jakarta.inject.Singleton

@Singleton
@Bean(preDestroy = "close")
open class Test : AutoCloseable {

    var closed = false

    override fun close() {
        closed = true
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)

        then:
        bean != null
        !bean.closed

        when:
        context.destroyBean(beanType)

        then:
        bean.closed

        cleanup:
        context.close()
    }

    void "test preDestroy declared on the bean class that does not exist"() {
        when:
        buildContext('''
package test

import io.micronaut.context.annotation.Bean
import jakarta.inject.Singleton

@Singleton
@Bean(preDestroy = "notthere")
open class Test {

    fun close() {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Bean defines a preDestroy method that does not exist or is not public: notthere")
    }
}
