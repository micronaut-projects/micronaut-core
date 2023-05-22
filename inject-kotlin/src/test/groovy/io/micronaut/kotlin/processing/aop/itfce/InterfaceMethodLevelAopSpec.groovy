package io.micronaut.kotlin.processing.aop.itfce

import io.micronaut.aop.Intercepted
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.kotlin.processing.aop.simple.CovariantClass
import spock.lang.Specification
import spock.lang.Unroll

class InterfaceMethodLevelAopSpec extends Specification {

    @Unroll
    void "test AOP method invocation for method #method"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()
        InterfaceClass foo = beanContext.getBean(InterfaceClass)

        expect:
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result

        where:
        method                        | args                   | result
        'test'                        | ['test']               | "Name is changed"                   // test for single string arg
        'test'                        | [10]                   | "Age is 20"                   // test for single primitive
        'test'                        | ['test', 10]           | "Name is changed and age is 10"    // test for multiple args, one primitive
        'test'                        | []                     | "noargs"                           // test for no args
        'testVoid'                    | ['test']               | null                   // test for void return type
        'testVoid'                    | ['test', 10]           | null                   // test for void return type
        'testBoolean'                 | ['test']               | true                   // test for boolean return type
        'testBoolean'                 | ['test', 10]           | true                  // test for boolean return type
        'testInt'                     | ['test']               | 1                   // test for int return type
        'testInt'                     | ['test', 10]           | 20                  // test for int return type
        'testShort'                   | ['test']               | 1                   // test for short return type
        'testShort'                   | ['test', 10]           | 20                  // test for short return type
        'testChar'                    | ['test']               | 1                   // test for char return type
        'testChar'                    | ['test', 10]                     | 20                  // test for char return type
        'testByte'                    | ['test']                         | 1                   // test for byte return type
        'testByte'                    | ['test', 10]                     | 20                  // test for byte return type
        'testFloat'                   | ['test']                         | 1                   // test for float return type
        'testFloat'                   | ['test', 10]                     | 20                  // test for float return type
        'testDouble'                  | ['test']                         | 1                   // test for double return type
        'testDouble'                  | ['test', 10]                     | 20                  // test for double return type
        'testByteArray'               | ['test', 'test'.bytes]           | 'test'.bytes        // test for byte array
        'testGenericsWithExtends'     | ['test', 10]                     | 'Name is changed'        // test for generics
        'testGenericsFromType'        | ['test', 10]                     | 'Name is changed'        // test for generics
        'testListWithWildCardIn'      | ['test', new CovariantClass<>()] | new CovariantClass<>('changed')        // test for generics
        'testListWithWildCardOut'     | ['test', new CovariantClass<>()] | new CovariantClass<>('changed')
    }


    void "test AOP setup"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when:
        InterfaceClass foo = beanContext.getBean(InterfaceClass)


        then:
        foo instanceof Intercepted
        foo.test("test") == "Name is changed"

        cleanup:
        beanContext.close()
    }

}
