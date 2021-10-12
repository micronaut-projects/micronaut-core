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

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class RegisterSingletonSpec extends Specification {

    void "test register singleton method"() {
        given:
        BeanContext context = new DefaultBeanContext().start()
        def b = new B()

        when:
        context.registerSingleton(b)

        then:
        context.getBean(B) == b
        context.getBeansOfType(B).size() == 1
        b.a != null
        b.a == context.getBean(A)

        cleanup:
        context.close()
    }


    void "test register named singleton method"() {
        given:
        BeanContext context = new DefaultBeanContext().start()
        def b = new B()

        when:
        context.registerSingleton(B, b, Qualifiers.byName("test"))

        then:
        context.getBean(B, Qualifiers.byName("test")) == b
        // there are 2 because currently defining only @Inject results in
        // another bean definition with the primary qualifier
        context.getBeansOfType(B).size() == 2
        // no bean definition for qualifier to not injected
        b.a == null

        cleanup:
        context.close()
    }

    @Singleton
    static class A {}

    static class B {
        B() {
            println "created"
        }
        @Inject A a
    }
}
