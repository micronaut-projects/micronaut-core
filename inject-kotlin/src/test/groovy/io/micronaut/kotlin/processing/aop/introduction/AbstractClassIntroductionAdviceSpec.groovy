package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.Intercepted
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification
import spock.lang.Unroll

class AbstractClassIntroductionAdviceSpec extends Specification {

    @Unroll
    void "test AOP method invocation @Named bean for method #method"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()
        AbstractClass foo = beanContext.getBean(AbstractClass)

        expect:
        foo instanceof Intercepted
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result

        where:
        method                 | args         | result
        'test'                 | ['test']     | "changed"                   // test for single string arg
        'nonAbstract'          | ['test']     | "changed"                   // test for single string arg
        'test'                 | ['test', 10] | "changed"    // test for multiple args, one primitive
        'testGenericsFromType' | ['test', 10] | "changed"    // test for multiple args, one primitive
    }
}
