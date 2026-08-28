package io.micronaut.kotlin.processing.aop.factory

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.kotlin.processing.aop.simple.CovariantClass
import org.hibernate.SessionFactory
import spock.lang.Specification
import spock.lang.Unroll

class AdviceDefinedOnInterfaceFactorySpec extends Specification {
    @Unroll
    void "test AOP method invocation @Named bean for method #method"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        InterfaceClass foo = context.getBean(InterfaceClass, Qualifiers.byName("another"))

        expect:
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result

        cleanup:
        context.close()

        where:
        method                        | args                   | result
        'test'                        | ['test']               | "Name is changed"                   // test for single string arg
        'test'                        | ['test', 10]           | "Name is changed and age is 10"    // test for multiple args, one primitive
        'test'                        | []                     | "noargs"                           // test for no args
        'testVoid'                    | ['test']               | null                   // test for void return type
        'testVoid'                    | ['test', 10]           | null                   // test for void return type
        'testBoolean'                 | ['test']               | true                   // test for boolean return type
        'testBoolean'                 | ['test', 10]                     | true                  // test for boolean return type
        'testInt'                     | ['test']                         | 1                   // test for int return type
        'testShort'                   | ['test']                         | 1                   // test for short return type
        'testChar'                    | ['test']                         | 1                   // test for char return type
        'testByte'                    | ['test']                         | 1                   // test for byte return type
        'testFloat'                   | ['test']                         | 1                   // test for float return type
        'testDouble'                  | ['test']                         | 1                   // test for double return type
        'testByteArray'               | ['test', 'test'.bytes]           | 'test'.bytes        // test for byte array
        'testGenericsWithExtends'     | ['test', 10]                     | 'Name is changed'        // test for generics
        'testGenericsFromType'        | ['test', 10]                     | 'Name is changed'        // test for generics
        'testListWithWildCardIn'      | ['test', new CovariantClass<>()] | new CovariantClass<>('changed')        // test for generics
        'testListWithWildCardOut'     | ['test', new CovariantClass<>()] | new CovariantClass<>('changed')        // test for generics
    }

    @Unroll
    void "test AOP method invocation for method #method"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        InterfaceClass foo = context.getBean(InterfaceClass)

        expect:
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result

        cleanup:
        context.close()

        where:
        method                        | args                   | result
        'test'                        | ['test']               | "Name is changed"                   // test for single string arg
        'test'                        | ['test', 10]           | "Name is changed and age is 10"    // test for multiple args, one primitive
        'test'                        | []                     | "noargs"                           // test for no args
        'testVoid'                    | ['test']               | null                   // test for void return type
        'testVoid'                    | ['test', 10]           | null                   // test for void return type
        'testBoolean'                 | ['test']               | true                   // test for boolean return type
        'testBoolean'                 | ['test', 10]           | true                  // test for boolean return type
        'testInt'                     | ['test']               | 1                   // test for int return type
        'testShort'                   | ['test']               | 1                   // test for short return type
        'testChar'                    | ['test']               | 1                   // test for char return type
        'testByte'                    | ['test']               | 1                   // test for byte return type
        'testFloat'                   | ['test']               | 1                   // test for float return type
        'testDouble'                  | ['test']               | 1                   // test for double return type
        'testByteArray'               | ['test', 'test'.bytes] | 'test'.bytes        // test for byte array
        'testGenericsWithExtends'     | ['test', 10]           | 'Name is changed'        // test for generics
        'testGenericsFromType'        | ['test', 10]           | 'Name is changed'        // test for generics
        'testListWithWildCardIn'      | ['test', new CovariantClass<>()]           | new CovariantClass<>('changed')        // test for generics
        'testListWithWildCardOut'     | ['test', new CovariantClass<>()]           | new CovariantClass<>('changed')        // test for generics
    }


    void "test session factory proxy"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        BeanDefinition<SessionFactory> beanDefinition = context.findBeanDefinition(SessionFactory).get()
        SessionFactory sessionFactory = context.getBean(SessionFactory)

        // make sure all the public method are implemented
        def clazz = sessionFactory.getClass()
        int count = 1 // proxy methods
        def interfaces = ReflectionUtils.getAllInterfaces(SessionFactory.class)
                .stream().filter {c -> !c.name.toLowerCase().contains("jacoco") }.toList()
        interfaces += SessionFactory.class
        for(i in interfaces) {
            for(m in i.declaredMethods) {
                if (m.name.contains("jacoco")) {
                    continue
                }
                count++
                assert clazz.getDeclaredMethod(m.name, m.parameterTypes)
            }
        }

        then:
        sessionFactory instanceof Intercepted

        cleanup:
        context.close()
    }

    void "test AOP setup"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        InterfaceClass foo = context.getBean(InterfaceClass)
        InterfaceClass another = context.getBean(InterfaceClass, Qualifiers.byName("another"))

        then:
        foo instanceof Intercepted
        another instanceof Intercepted
        // should not be a reflection based method
        foo.test("test") == "Name is changed"

        cleanup:
        context.close()
    }
}
