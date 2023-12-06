package io.micronaut.aop

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ContextualMyInterfaceInterceptorSpec extends Specification {

    void "test custom interceptor"() {
        given:
        ApplicationContext context = ApplicationContext.builder("test").build()
        context.registerSingleton(Double.valueOf(100))
        context.start()
        def myInterface = context.getBean(MyInterface)

        when:"Intercepted MyInterface does have methods implemented"
        def result1 = myInterface.process("abc")
        def result2 = myInterface.process("abc", 1)
        def result3 = myInterface.process("abc", new int[] {1, 2})
        then:
        result1 == "process1"
        result2 == "process2"
        result3 == "process3"

        cleanup:
        context.close()
    }

}
