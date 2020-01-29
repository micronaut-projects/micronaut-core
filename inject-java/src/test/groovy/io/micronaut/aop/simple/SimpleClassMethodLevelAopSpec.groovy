/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.simple

import io.micronaut.aop.Intercepted
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleClassMethodLevelAopSpec extends Specification {

    @Unroll
    void "test AOP method invocation for method #method"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()
        SimpleClass foo = beanContext.getBean(SimpleClass)

        expect:
        args.isEmpty() ? foo."$method"() : foo."$method"(*args) == result
        foo.postConstructInvoked

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
        'testChar'                    | ['test', 10]           | 20                  // test for char return type
        'testByte'                    | ['test']               | 1                   // test for byte return type
        'testByte'                    | ['test', 10]           | 20                  // test for byte return type
        'testFloat'                   | ['test']               | 1                   // test for float return type
        'testFloat'                   | ['test', 10]           | 20                  // test for float return type
        'testDouble'                  | ['test']               | 1                   // test for double return type
        'testDouble'                  | ['test', 10]           | 20                  // test for double return type
        'testByteArray'               | ['test', 'test'.bytes] | 'test'.bytes        // test for byte array
        'testGenericsWithExtends'     | ['test', 10]           | 'Name is changed'        // test for generics
        'testGenericsFromType'        | ['test', 10]           | 'Name is changed'        // test for generics
        'testListWithWildCardSuper'   | ['test', []]           | ['changed']        // test for generics
        'testListWithWildCardExtends' | ['test', []]           | ['changed']        // test for generics
    }


    void "test AOP setup"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when: "the bean definition is obtained"
        BeanDefinition<SimpleClass> beanDefinition = beanContext.findBeanDefinition(SimpleClass).get()

        then:
        beanDefinition.findMethod("test", String).isPresent()
        // should not be a reflection based method
        !beanDefinition.findMethod("test", String).get().getClass().getName().contains("Reflection")

        when:
        SimpleClass foo = beanContext.getBean(SimpleClass)


        then:
        foo instanceof Intercepted
        beanContext.findExecutableMethod(SimpleClass, "test", String).isPresent()
        // should not be a reflection based method
        !beanContext.findExecutableMethod(SimpleClass, "test", String).get().getClass().getName().contains("Reflection")
        foo.test("test") == "Name is changed"
    }

    void "test modifying the interceptor parameters is not supported"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()
        SimpleClass foo = beanContext.getBean(SimpleClass)

        when: "the interceptor is called"
        foo.invalidInterceptor()

        then:
        thrown(UnsupportedOperationException)
    }

}
