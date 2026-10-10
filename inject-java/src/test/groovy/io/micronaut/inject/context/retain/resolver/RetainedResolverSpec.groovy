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
package io.micronaut.inject.context.retain.resolver

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanDependencyResolver
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

/**
 * The resolver a retained bean received is bound to the context that created the bean: the adopting context keeps what
 * the bean owns under a resolver of its own, so the stopped context is not kept reachable through it.
 */
class RetainedResolverSpec extends Specification {

    def setup() {
        Channel.CLOSED.set(0)
    }

    void "what a retained bean resolved through its resolver is owned through a resolver of the adopting context"() {
        given:
        ApplicationContext first = start(List.of())
        Connection connection = first.getBean(Connection)

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining { Connection.isAssignableFrom(it.beanType) }
        ApplicationContext second = start(retained)
        BeanRegistration<?> adopted = second.getBeanRegistration(Connection, null)
        List<BeanRegistration<?>> resolvers = adopted.dependentBeans().findAll { it.bean instanceof BeanDependencyResolver }

        then: "the connection is adopted, and the resolver it owns is one of the adopting context"
        adopted.bean.is(connection)
        resolvers.size() == 1
        resolvers[0].bean.@context.is(second)
        !resolvers[0].bean.is(retained.find { it.bean.is(connection) }.dependentBeans().find { it.bean instanceof BeanDependencyResolver }.bean)

        and: "it still owns the channel the connection resolved"
        resolvers[0].dependentBeans()*.bean.any { it.is(connection.channel) }

        and: "the connection's destruction is still ordered before the broker it resolved, through a registration of the adopting context"
        resolvers[0].dependencies.requiredBeans().any {
            it.bean.is(connection.broker) && it.@beanContext.is(second) && it.beanDefinition.is(second.getBeanDefinition(Broker))
        }
        second.getBean(Broker).is(connection.broker)
        Channel.CLOSED.get() == 0

        when:
        second.close()

        then: "the channel is destroyed with the connection, once"
        Channel.CLOSED.get() == 1
    }

    void "a configuration bean the resolver resolved under a prefix that releases the bean is neither retained nor held"() {
        given:
        ApplicationContext first = start(List.of())
        Connection connection = first.getBean(Connection)
        ConnectionSettings settings = first.getBean(ConnectionSettings)

        when:
        Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) first).stopRetaining(new DefaultBeanContext.RetentionCriteria() {
            @Override
            boolean retain(BeanRegistration<?> registration) {
                return Connection.isAssignableFrom(registration.beanType)
            }

            @Override
            Set<String> invalidatedBy(BeanRegistration<?> registration) {
                return Set.of('resolver.connection')
            }
        })
        ApplicationContext second = start(retained)
        BeanRegistration<?> resolver = second.getBeanRegistration(Connection, null).dependentBeans().find { it.bean instanceof BeanDependencyResolver }

        then: "the connection is adopted, ordered before the broker it resolved, and holds nothing of the stopped context's configuration"
        second.getBean(Connection).is(connection)
        !retained*.bean.any { it.is(settings) }
        resolver.dependencies.requiredBeans().any { it.bean.is(connection.broker) }
        !resolver.dependencies.requiredBeans().any { it.bean.is(settings) }
        !second.getBean(ConnectionSettings).is(settings)

        cleanup:
        second?.close()
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        return ApplicationContext.builder()
            .properties(['spec.name': 'RetainedResolverSpec'])
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }
}
