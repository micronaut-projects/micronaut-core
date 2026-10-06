/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.context.retain.annotated

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Retain
import io.micronaut.context.reload.AnnotatedBeanRetentionPolicy
import spock.lang.Specification

/**
 * A module declares with {@link Retain} which of its beans survive a restart, on the bean's class or on its factory
 * method, and the annotated policy reads it from the definition.
 */
class RetainAnnotationSpec extends Specification {

    def setup() {
        AnnotatedPool.CREATED.set(0)
        AnnotatedPool.DESTROYED.set(0)
    }

    void "an annotated bean and an annotated factory product survive a restart, the others are created again"() {
        given: "a running context whose beans were all created"
        ApplicationContext first = start(List.of())
        AnnotatedPool pool = first.getBean(AnnotatedPool)
        ProducedPool produced = first.getBean(ProducedPool)
        OtherProduct other = first.getBean(OtherProduct)
        PlainPool plain = first.getBean(PlainPool)

        when: "it stops retaining what the annotated policy retains"
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining(AnnotatedBeanRetentionPolicy.INSTANCE::retain)

        then: "the annotated class and the annotated factory method's product are retained, undestroyed"
        retained*.bean.any { it.is(pool) }
        retained*.bean.any { it.is(produced) }
        !retained*.bean.any { it.is(other) }
        !retained*.bean.any { it.is(plain) }
        AnnotatedPool.DESTROYED.get() == 0

        when: "a new context adopts them"
        ApplicationContext second = start(retained)

        then: "it serves the same instances, and creates the others again"
        second.getBean(AnnotatedPool).is(pool)
        second.getBean(ProducedPool).is(produced)
        !second.getBean(OtherProduct).is(other)
        !second.getBean(PlainPool).is(plain)
        AnnotatedPool.CREATED.get() == 1

        when:
        second.close()

        then: "the retained bean is destroyed once, with the context that adopted it"
        AnnotatedPool.DESTROYED.get() == 1
    }

    void "an annotated bean that holds the context is refused"() {
        given:
        ApplicationContext first = start(List.of())
        ContextHoldingPool holding = first.getBean(ContextHoldingPool)
        AnnotatedPool pool = first.getBean(AnnotatedPool)

        expect: "the policy asks for it"
        AnnotatedBeanRetentionPolicy.INSTANCE.retain(first.getBeanRegistration(ContextHoldingPool, null))

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining(AnnotatedBeanRetentionPolicy.INSTANCE::retain)

        then: "the context refuses it, and retains the safe one"
        !retained*.bean.any { it.is(holding) }
        retained*.bean.any { it.is(pool) }

        when:
        ApplicationContext second = start(retained)

        then:
        !second.getBean(ContextHoldingPool).is(holding)
        second.getBean(ContextHoldingPool).context.is(second)

        cleanup:
        second?.close()
    }

    void "the annotation is read from the declared metadata: an annotated factory is retained, not what it produces"() {
        given:
        ApplicationContext context = start(List.of())
        context.getBean(FactoryProduct)

        expect:
        AnnotatedBeanRetentionPolicy.INSTANCE.retain(context.getBeanRegistration(RetainedFactory, null))
        !AnnotatedBeanRetentionPolicy.INSTANCE.retain(context.getBeanRegistration(FactoryProduct, null))
        !AnnotatedBeanRetentionPolicy.INSTANCE.retain(context.getBeanRegistration(PlainPool, null))

        cleanup:
        context.close()
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        ApplicationContext.builder()
            .properties('spec.name': 'RetainAnnotationSpec')
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()
    }
}
