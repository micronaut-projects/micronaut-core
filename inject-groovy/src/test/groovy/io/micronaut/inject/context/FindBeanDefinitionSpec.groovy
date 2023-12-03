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
package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Primary
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class FindBeanDefinitionSpec extends Specification {

    void 'test find bean definition'() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.findBeanDefinition(A).get().beanType == B
        ctx.findBeanDefinition(B).get().beanType == B
        ctx.findBeanDefinition(IA).get().beanType == B
        ctx.findBeanDefinition(IB).get().beanType == B
        ctx.findBeanDefinition(Ab).get().beanType == B
        ctx.getBean(A, Qualifiers.byName("A")) instanceof A
        !(ctx.getBean(A, Qualifiers.byName("A")) instanceof B)
        ctx.getBean(A) instanceof B
        ctx.getBean(Ab) instanceof B
        ctx.getBean(IA) instanceof B
        ctx.getBean(IB) instanceof B
        ctx.getBean(B) instanceof B

        cleanup:
        ctx.close()
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
