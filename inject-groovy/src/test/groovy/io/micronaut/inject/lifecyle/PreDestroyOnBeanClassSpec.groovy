package io.micronaut.inject.lifecyle

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext

class PreDestroyOnBeanClassSpec extends AbstractBeanDefinitionSpec {

    void "test preDestroy declared on the bean class"() {
        given:
        ApplicationContext context = buildContext('''
package predestroyclass

import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
@Bean(preDestroy = "close")
class Test implements AutoCloseable {

    boolean closed = false

    @Override
    void close() {
        closed = true
    }
}
''')

        when:
        Class<?> beanType = context.classLoader.loadClass('predestroyclass.Test')
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
package predestroyclass2

import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
@Bean(preDestroy = "notthere")
class Test {

    void close() {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Bean defines a preDestroy method that does not exist or is not public: notthere")
    }
}
