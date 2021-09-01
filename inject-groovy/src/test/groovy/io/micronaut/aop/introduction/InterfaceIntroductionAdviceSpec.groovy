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
package io.micronaut.aop.introduction

import io.micronaut.aop.Intercepted
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class InterfaceIntroductionAdviceSpec extends AbstractBeanDefinitionSpec {

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
package test;

import java.util.List;
import io.micronaut.aop.introduction.ParentInterface;
import io.micronaut.aop.introduction.Stub;

@Stub
interface Test extends ParentInterface<List<String>> {
}
""")

        expect:
        !definition.getTypeArguments(ParentInterface).isEmpty()
    }
}
