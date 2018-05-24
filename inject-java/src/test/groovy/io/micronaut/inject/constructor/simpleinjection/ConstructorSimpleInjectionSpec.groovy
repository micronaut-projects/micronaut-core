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
package io.micronaut.inject.constructor.simpleinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class ConstructorSimpleInjectionSpec extends Specification {

    void "test injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)
        B2 b2 =  context.getBean(B2)

        then:"The implementation is injected"
        b.a != null
        b2.a != null
        b2.a2 != null

        when:
        BeanDefinition bd = context.getBeanDefinition(B)
        BeanDefinition bd2 = context.getBeanDefinition(B2)

        then: "The constructor argument is added to the required components"
        bd.getRequiredComponents().size() == 1
        bd.getRequiredComponents().contains(A)
        bd2.getRequiredComponents().size() == 1
        bd2.getRequiredComponents().contains(A)
    }
}