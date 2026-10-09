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
package io.micronaut.inject.context.retain.observed

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.reload.AnnotatedBeanRetentionPolicy
import io.micronaut.context.reload.PolicyRetentionCriteria
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * A retained bean is released by a change under the prefix of any configuration bean in its closure, which its
 * retention does not have to name: the configuration of the factory that produced it, an {@code @EachProperty} entry
 * under its own prefix, and nested configuration.
 */
class ObservedConfigurationSpec extends Specification {

    private static final Map<String, Object> PROPERTIES = [
        'spec.name'                 : 'ObservedConfigurationSpec',
        'observed.client.timeout'   : '5',
        'observed.client.pool.size' : '3',
        'observed.servers.a.port'   : '1',
        'observed.servers.b.port'   : '2',
        'observed.unrelated.value'  : 'x'
    ]

    void "with no change every retained bean is kept, without the configuration beans it was made from"() {
        given:
        ApplicationContext first = start(List.of(), PROPERTIES)
        Map<String, Object> beans = beansOf(first)
        Object settings = first.getBean(ClientSettings)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, [])
        ApplicationContext second = start(retained, PROPERTIES)

        then: "the next context serves the same beans, made from configuration beans of its own"
        beansOf(second).every { name, bean -> bean.is(beans[name]) }
        !retained*.bean.any { it instanceof ClientSettings || it instanceof ClientSettings.PoolSettings || it instanceof ServerSettings }
        !second.getBean(ClientSettings).is(settings)

        cleanup:
        second?.close()
    }

    void "a change under the configuration of the factory releases its products, whatever prefixes they name"() {
        given:
        ApplicationContext first = start(List.of(), PROPERTIES)
        Map<String, Object> beans = beansOf(first)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, ['observed.client.timeout'])
        ApplicationContext second = start(retained, PROPERTIES + ['observed.client.timeout': '9'])
        Map<String, Object> next = beansOf(second)

        then: "the products made from the changed configuration are made again from it"
        !next.unnamed.is(beans.unnamed)
        next.unnamed.value == 9
        !next.other.is(beans.other)
        next.other.value == 9

        and: "the bean of the nested pool configuration, which the change did not touch, is kept"
        next.pool.is(beans.pool)

        and: "the connections, made from other configuration, are kept"
        next.a.is(beans.a)
        next.b.is(beans.b)

        cleanup:
        second?.close()
    }

    void "a change under nested configuration releases the bean that received it"() {
        given:
        ApplicationContext first = start(List.of(), PROPERTIES)
        Map<String, Object> beans = beansOf(first)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, ['observed.client.pool.size'])
        ApplicationContext second = start(retained, PROPERTIES + ['observed.client.pool.size': '8'])
        Map<String, Object> next = beansOf(second)

        then: "the change is also under the factory's prefix, which holds the nested one"
        !next.pool.is(beans.pool)
        next.pool.size == 8
        !next.unnamed.is(beans.unnamed)
        next.a.is(beans.a)

        cleanup:
        second?.close()
    }

    void "a prefix the retention names still releases the bean that names it, and only it"() {
        given:
        ApplicationContext first = start(List.of(), PROPERTIES)
        Map<String, Object> beans = beansOf(first)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, ['observed.unrelated.value'])

        then:
        !retained*.bean.any { it.is(beans.other) }
        retained*.bean.any { it.is(beans.unnamed) }
        retained*.bean.any { it.is(beans.pool) }

        cleanup:
        start(retained, PROPERTIES).close()
    }

    void "a change under an @EachProperty entry releases only the beans made from that entry"() {
        given:
        ApplicationContext first = start(List.of(), PROPERTIES)
        Map<String, Object> beans = beansOf(first)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, ['observed.servers.a.port'])
        ApplicationContext second = start(retained, PROPERTIES + ['observed.servers.a.port': '7'])
        Map<String, Object> next = beansOf(second)

        then:
        !next.a.is(beans.a)
        next.a.port == 7
        next.b.is(beans.b)
        next.unnamed.is(beans.unnamed)
        next.pool.is(beans.pool)

        cleanup:
        second?.close()
    }

    void "a bean holding a provider of configuration is not retained, since the provider resolves through the stopped context"() {
        given:
        ApplicationContext first = start(List.of(), PROPERTIES)
        ProviderUser user = first.getBean(ProviderUser)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, [])

        then:
        !retained*.bean.any { it.is(user) }

        cleanup:
        start(retained, PROPERTIES).close()
    }

    private static Map<String, Object> beansOf(ApplicationContext context) {
        return [
            unnamed: context.getBean(Client, Qualifiers.byName('unnamed')),
            other  : context.getBean(Client, Qualifiers.byName('other')),
            pool   : context.getBean(PoolUser),
            a      : context.getBean(Connections.Connection, Qualifiers.byName('a')),
            b      : context.getBean(Connections.Connection, Qualifiers.byName('b'))
        ]
    }

    private static Collection<BeanRegistration<?>> stopRetaining(ApplicationContext context, List<String> changedKeys) {
        ConfigurationChange change = ConfigurationChange.ofKeys(changedKeys as Set<String>)
        return ((DefaultBeanContext) context).stopRetaining(new PolicyRetentionCriteria(List.of(AnnotatedBeanRetentionPolicy.INSTANCE),
            { String prefix -> change.touches(prefix) }, { false }))
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained, Map<String, Object> properties) {
        return ApplicationContext.builder()
            .properties(properties)
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }
}
