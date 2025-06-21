package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition

class InterfaceIntroductionAdviceSpec extends Specification {

    @Unroll
    void "test AOP method invocation @Named bean for method #method"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        InterfaceIntroductionClass foo = context.getBean(InterfaceIntroductionClass)

        expect:
        foo instanceof Intercepted
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result

        cleanup:
        context.close()

        where:
        method                 | args         | result
        'test'                 | ['test']     | "changed"                   // test for single string arg
        'test'                 | ['test', 10] | "changed"    // test for multiple args, one primitive
        'testGenericsFromType' | ['test', 10] | "changed"    // test for multiple args, one primitive
    }

    void "test injecting an introduction advice with generics"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.getBean(InjectParentInterface)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
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
