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
package io.micronaut.docs.devmode.watch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.env.DevelopmentMode
import io.micronaut.context.reload.ClassChange
import io.micronaut.context.reload.ClassChangeEvent
import io.micronaut.context.reload.ReloadStrategy
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.context.watch.ReloadingConfigurationWatcher.Outcome
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.web.router.RouteBuilder
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.function.Supplier

class BeanWatchSnippetsSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder(
            'spec.name': 'BeanWatchSnippetsSpec',
            'pools.default.url': 'jdbc:h2:mem:one',
            'pools.default.username': 'sa',
            'pools.default.password': 'one',
            (DevelopmentMode.PROPERTY): true
    ).start()

    private static ClassChangeEvent retiringThisLoader() {
        // every class of the test's loader is a class of the retired generation
        ClassLoader loader = BeanWatchSnippetsSpec.classLoader
        new ClassChangeEvent(BeanWatchSnippetsSpec, Set.of(loader), loader,
                [new ClassChange(StringSerializer.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RESTART)
    }

    void "the route table follows the route builders registered"() {
        given:
        RouteTable table = context.getBean(RouteTable)
        int before = table.builders().size()

        when:
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(RouteBuilder, { throw new UnsupportedOperationException() } as Supplier<RouteBuilder>).singleton(true).build())

        then:
        table.builders().size() == before + 1
    }

    void "the codec handlers see each addition"() {
        given:
        CodecRegistry registry = context.getBean(CodecRegistry)

        expect:
        registry.codecs()*.beanType == [JsonCodec]

        when:
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Codec, { ({ -> 'text/plain' } as Codec) } as Supplier<Codec>).named('text').singleton(true).build())

        then:
        registry.codecs().size() == 2
    }

    void "the pool applies new credentials and is recreated for a new URL"() {
        given:
        DefaultBeanContext beanContext = (DefaultBeanContext) context
        ConnectionPool pool = context.getBean(ConnectionPool, Qualifiers.byName('default'))

        expect:
        beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of('pools.default.password'))) == [Outcome.APPLIED]
        pool.evictions == 1
        context.getBean(ConnectionPool, Qualifiers.byName('default')).is(pool)
        beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of('pools.default.maximum-pool-size'))) == [Outcome.IGNORED]
        beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of('pools.default.url'))) == [Outcome.RECREATE]
        !context.getBean(ConnectionPool, Qualifiers.byName('default')).is(pool)
    }

    void "the serializer registry builds its state from the first batch"() {
        expect:
        context.getBean(SerializerRegistry).serializers()*.beanType == [StringSerializer]
    }

    void "the cache forgets the classes a reload retires"() {
        given:
        TypeDescriptionCache cache = context.getBean(TypeDescriptionCache)
        cache.describe(StringSerializer)
        cache.describe(String)

        when:
        context.publishEvent(retiringThisLoader())

        then: 'String comes from the JDK, not from the retired generation'
        cache.size() == 1
    }

    void "the lookup and its dependents are recreated when a serializer class is retired"() {
        given:
        Writer writer = context.getBean(Writer)
        SerializerLookup lookup = writer.lookup

        expect:
        writer.write('a') == '"a"'

        when:
        context.publishEvent(retiringThisLoader())
        Writer recreated = context.getBean(Writer)

        then:
        !recreated.is(writer)
        !recreated.lookup.is(lookup)
        recreated.lookup.is(context.getBean(SerializerLookup))
        recreated.write('b') == '"b"'
    }
}
