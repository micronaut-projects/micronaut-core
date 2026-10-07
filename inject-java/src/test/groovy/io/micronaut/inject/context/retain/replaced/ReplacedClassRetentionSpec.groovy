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
package io.micronaut.inject.context.retain.replaced

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

/**
 * A retained bean runs what it received: a bean that received a class the restart replaces is not retained, since it
 * would keep the old generation of the application reachable and running.
 */
class ReplacedClassRetentionSpec extends Specification {

    def setup() {
        AuthenticatedClient.CREATED.set(0)
    }

    void "a bean that received an instance of a class the restart replaces is not retained"() {
        given:
        ApplicationContext first = start(List.of())
        AuthenticatedClient client = first.getBean(AuthenticatedClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it == AppCredentials }

        then: "neither the client nor the credentials it holds are retained"
        !retained*.bean.any { it.is(client) || it.is(client.credentials) }

        when:
        ApplicationContext second = start(retained)

        then: "the next context creates a client with its own credentials"
        !second.getBean(AuthenticatedClient).is(client)
        second.getBean(AuthenticatedClient).credentials.is(second.getBean(Credentials))
        AuthenticatedClient.CREATED.get() == 2

        cleanup:
        second?.close()
    }

    void "a bean that received nothing the restart replaces is retained with what it holds"() {
        given:
        ApplicationContext first = start(List.of())
        AuthenticatedClient client = first.getBean(AuthenticatedClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { false }
        ApplicationContext second = start(retained)

        then:
        second.getBean(AuthenticatedClient).is(client)
        AuthenticatedClient.CREATED.get() == 1

        cleanup:
        second?.close()
    }

    private static Collection<BeanRegistration<?>> stopRetaining(ApplicationContext context, Closure<Boolean> replaced) {
        return ((DefaultBeanContext) context).stopRetaining(new DefaultBeanContext.RetentionCriteria() {
            @Override
            boolean retain(BeanRegistration<?> registration) {
                return registration.beanType == AuthenticatedClient
            }

            @Override
            Set<String> invalidatedBy(BeanRegistration<?> registration) {
                return Set.of()
            }

            @Override
            boolean isReplaced(Class<?> type) {
                return replaced.call(type)
            }
        })
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        return ApplicationContext.builder()
            .properties(['spec.name': 'ReplacedClassRetentionSpec'])
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()
    }
}
