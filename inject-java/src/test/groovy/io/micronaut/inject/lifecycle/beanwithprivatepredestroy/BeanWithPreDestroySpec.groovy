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
package io.micronaut.inject.lifecycle.beanwithprivatepredestroy

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class BeanWithPreDestroySpec extends Specification{

    void "test that a bean with a pre-destroy hook works"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
            B b = context.getBean(B)

        then:
        b.a != null
        !b.noArgsDestroyCalled
        !b.injectedDestroyCalled

        when:
        context.destroyBean(B)

        then:
        b.noArgsDestroyCalled
        b.injectedDestroyCalled

        cleanup:
        context.close()
    }

    void "test that a bean with a pre-destroy hook works closed on close"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
            B b = context.getBean(B)

        then:
        b.a != null
        !b.noArgsDestroyCalled
        !b.injectedDestroyCalled

        when:
        context.close()

        then:
        b.noArgsDestroyCalled
        b.injectedDestroyCalled
    }

    void "test that destroy events run in the right phase"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()


        when:
        def pre = context.getBean(CPreDestroyEventListener)
        def post = context.getBean(CDestroyedListener)
        def c = context.getBean(C)

        then:
        !c.isClosed()

        when:
        context.close()

        then:
        pre.called
        post.called
        c.isClosed()
    }
}
