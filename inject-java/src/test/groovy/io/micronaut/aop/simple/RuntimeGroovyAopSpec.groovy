package io.micronaut.aop.simple


import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class RuntimeGroovyAopSpec extends Specification {

    void "test Groovy runtime"() {
        given:
            ApplicationContext context = ApplicationContext.run()
            RuntimeGroovyProxySimpleClass bean = context.getBean(RuntimeGroovyProxySimpleClass)

        expect:
            bean.test("hello") == "Name is changed"

        cleanup:
            context.close()
    }

}
