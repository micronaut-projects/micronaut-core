/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
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
