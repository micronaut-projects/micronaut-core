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
package io.micronaut.inject.context.retain.factory

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * A connection pool as a data source is: made by a factory, and wrapped by a bean created listener into the bean
 * the context registers, a wrapper that holds the context's bean locator.
 */
class RetainedFactoryProductSpec extends Specification {

    def setup() {
        SimplePool.CREATED.set(0)
        SimplePool.CLOSED.set(0)
    }

    void "a retained bean a listener wrapped is retained as the listener received it, and wrapped again by each adopting context"() {
        given: "a context whose pool the listener wrapped"
        ApplicationContext first = start(List.of())
        def firstWrapper = (ContextualPools.Wrapper) first.getBean(DataPool, Qualifiers.byName('main'))
        DataPool pool = firstWrapper.target
        firstWrapper.locator().is(first)

        when: "it stops retaining the pools"
        Collection<BeanRegistration<?>> retained = stopRetainingPools(first)

        then: "the pool is retained as the listener received it, not the wrapper bound to the stopped context, and it is not closed"
        retained*.bean.any { it.is(pool) }
        !retained*.bean.any { it.is(firstWrapper) }
        !pool.closed
        SimplePool.CLOSED.get() == 0

        when: "a new context adopts it"
        ApplicationContext second = start(retained)
        def secondWrapper = (ContextualPools.Wrapper) second.getBean(DataPool, Qualifiers.byName('main'))

        then: "its own listener wrapped the same pool again, bound to the new context, and that wrapper is what is injected"
        !secondWrapper.is(firstWrapper)
        secondWrapper.target.is(pool)
        secondWrapper.locator().is(second)
        second.getBean(PoolUser).pool.is(secondWrapper)
        second.getBeansOfType(DataPool).every { it instanceof ContextualPools.Wrapper && it.locator().is(second) }
        SimplePool.CREATED.get() == 1
        !pool.closed

        when: "the next restart retains it again"
        retained = stopRetainingPools(second)
        ApplicationContext third = start(retained)
        def thirdWrapper = (ContextualPools.Wrapper) third.getBean(DataPool, Qualifiers.byName('main'))

        then: "the same pool, wrapped for the third context"
        retained*.bean.any { it.is(pool) }
        thirdWrapper.target.is(pool)
        thirdWrapper.locator().is(third)
        SimplePool.CREATED.get() == 1
        !pool.closed

        when: "the last context stops without retaining it"
        third.close()

        then: "the pool is closed once, as its own bean"
        pool.closed
        SimplePool.CLOSED.get() == 1
    }

    void "a product of a factory that holds the context is not retained, since destroying the factory closes it"() {
        given: "a pool made by a factory that holds the context and closes what it made"
        ApplicationContext first = start(List.of(), ['factory-pools.contextual': 'true'])
        def wrapper = (ContextualPools.Wrapper) first.getBean(DataPool, Qualifiers.byName('main'))

        when:
        Collection<BeanRegistration<?>> retained = stopRetainingPools(first)

        then: "the pool is not retained, and the factory closed it"
        retained.empty
        wrapper.target.closed
    }

    private static Collection<BeanRegistration<?>> stopRetainingPools(ApplicationContext context) {
        return ((DefaultBeanContext) context).stopRetaining { DataPool.isAssignableFrom(it.beanType) }
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained, Map<String, Object> extra = [:]) {
        return ApplicationContext.builder()
            .properties(['spec.name': 'RetainedFactoryProductSpec', 'factory-pools.main.size': '2'] + extra)
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()
    }
}
