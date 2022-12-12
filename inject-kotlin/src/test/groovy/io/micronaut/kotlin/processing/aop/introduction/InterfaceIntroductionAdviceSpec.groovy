package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.Intercepted
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification
import spock.lang.Unroll
import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class InterfaceIntroductionAdviceSpec extends Specification {

    @Unroll
    void "test AOP method invocation @Named bean for method #method"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()
        InterfaceIntroductionClass foo = beanContext.getBean(InterfaceIntroductionClass)

        expect:
        foo instanceof Intercepted
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result

        where:
        method                 | args         | result
        'test'                 | ['test']     | "changed"                   // test for single string arg
        'test'                 | ['test', 10] | "changed"    // test for multiple args, one primitive
        'testGenericsFromType' | ['test', 10] | "changed"    // test for multiple args, one primitive
    }

    void "test injecting an introduction advice with generics"() {
        BeanContext beanContext = new DefaultBeanContext().start()

        when:
        InjectParentInterface foo = beanContext.getBean(InjectParentInterface)

        then:
        noExceptionThrown()

        cleanup:
        beanContext.close()
    }

    void "test typeArgumentsMap are created for introduction advice"() {
        def definition = buildBeanDefinition("test.Test\$Intercepted", """
package test

import io.micronaut.kotlin.processing.aop.introduction.*

@Stub
interface Test: ParentInterface<List<String>>
""")

        expect:
        !definition.getTypeArguments(ParentInterface).isEmpty()
    }
}
