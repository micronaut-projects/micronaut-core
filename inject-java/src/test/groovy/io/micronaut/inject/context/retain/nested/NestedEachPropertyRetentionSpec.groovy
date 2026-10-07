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
package io.micronaut.inject.context.retain.nested

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

/**
 * An {@code @EachProperty} entry nested in another one is found by a new context only once it walks the configuration
 * of the outer entry, so the adopting context makes it again from the configuration path it was created under.
 */
class NestedEachPropertyRetentionSpec extends Specification {

    def setup() {
        ServerClient.CREATED.set(0)
        ServerClient.CLOSED.set(0)
    }

    void "a bean whose configuration received nested entries is adopted by the next context"() {
        given:
        ApplicationContext first = start(List.of(), ['nested-servers.main.streams.orders.size': '1'])
        ServerClient client = first.getBean(ServerClient)

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining { ServerClient.isAssignableFrom(it.beanType) }

        then:
        retained*.bean.any { it.is(client) }
        ServerClient.CLOSED.get() == 0

        when: "a new context adopts it before it walked the server's configuration"
        ApplicationContext second = start(retained, ['nested-servers.main.streams.orders.size': '1'])

        then: "it serves the same client, which is neither closed nor created again"
        second.getBean(ServerClient).is(client)
        second.getBeansOfType(ServerClient).size() == 1
        client.streams == ['orders']
        second.getBean(ServerConfig).streams*.name == ['orders']
        ServerClient.CREATED.get() == 1
        ServerClient.CLOSED.get() == 0

        when:
        second.close()

        then: "it is closed once, with the context that adopted it"
        ServerClient.CLOSED.get() == 1
    }

    void "a bean whose configuration received a nested entry the next context no longer configures is destroyed"() {
        given:
        ApplicationContext first = start(List.of(), ['nested-servers.main.streams.orders.size': '1'])
        ServerClient client = first.getBean(ServerClient)
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining { ServerClient.isAssignableFrom(it.beanType) }

        when:
        ApplicationContext second = start(retained, ['nested-servers.main.streams.events.size': '1'])

        then: "the client of the removed stream is closed, and the server has a client of its new stream"
        ServerClient.CLOSED.get() == 1
        second.getBean(ServerClient).streams == ['events']
        !second.getBean(ServerClient).is(client)

        cleanup:
        second?.close()
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained, Map<String, Object> properties) {
        return ApplicationContext.builder()
            .properties(['spec.name': 'NestedEachPropertyRetentionSpec'] + properties)
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()
    }
}
