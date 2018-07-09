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
package io.micronaut.inject.context

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Primary
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import javax.inject.Named
import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class FindBeanDefinitionSpec extends Specification {

    void 'test find bean definition'() {
        given:
        BeanContext beanContext = BeanContext.run()

        expect:
        beanContext.findBeanDefinition(A).get().beanType == B
        beanContext.findBeanDefinition(B).get().beanType == B
        beanContext.findBeanDefinition(IA).get().beanType == B
        beanContext.findBeanDefinition(IB).get().beanType == B
        beanContext.findBeanDefinition(Ab).get().beanType == B
        beanContext.getBean(A, Qualifiers.byName("A")) instanceof A
        !(beanContext.getBean(A, Qualifiers.byName("A")) instanceof B)
        beanContext.getBean(A) instanceof B
        beanContext.getBean(Ab) instanceof B
        beanContext.getBean(IA) instanceof B
        beanContext.getBean(IB) instanceof B
        beanContext.getBean(B) instanceof B
    }


    static abstract class Ab {}

    @Singleton
    @Named("A")
    static class A extends Ab implements IA{

    }

    static interface IB {

    }
    static interface IA {

    }

    @Singleton
    @Primary
    static class B extends A implements IB{

    }
}
