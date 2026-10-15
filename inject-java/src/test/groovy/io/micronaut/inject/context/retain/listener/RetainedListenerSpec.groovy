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
package io.micronaut.inject.context.retain.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

/**
 * The adopting context applies its own bean created listeners to every retained bean, as its creation would have, so a
 * listener the application changed applies to a retained bean too.
 */
class RetainedListenerSpec extends Specification {

    void "the listeners of the adopting context run on a retained bean they did not replace"() {
        given:
        ApplicationContext first = start(List.of(), 'first')
        ListenedPool pool = first.getBean(ListenedPool)

        expect:
        pool.configuredBy == ['first']

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining { ListenedPool.isAssignableFrom(it.beanType) }
        ApplicationContext second = start(retained, 'second')

        then: "the same pool, configured again by the listener of the adopting context"
        second.getBean(ListenedPool).is(pool)
        pool.configuredBy == ['first', 'second']

        when: "a context without the listener adopts it"
        retained = ((DefaultBeanContext) second).stopRetaining { ListenedPool.isAssignableFrom(it.beanType) }
        ApplicationContext third = start(retained, null)

        then:
        third.getBean(ListenedPool).is(pool)
        pool.configuredBy == ['first', 'second']

        cleanup:
        third?.close()
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained, String configurer) {
        Map<String, Object> properties = ['spec.name': 'RetainedListenerSpec']
        if (configurer != null) {
            properties['pool.configurer'] = configurer
        }
        return ApplicationContext.builder()
            .properties(properties)
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }
}
