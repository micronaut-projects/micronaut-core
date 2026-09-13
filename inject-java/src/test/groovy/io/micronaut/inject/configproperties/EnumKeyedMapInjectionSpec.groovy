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
package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class EnumKeyedMapInjectionSpec extends AbstractTypeElementSpec {

    void 'test an enum-keyed Map of @EachProperty beans is populated from configuration'() {
        given: 'the reproducer from #12477, holding a map of beans and a map of scalars'
        ApplicationContext context = buildContext('test.AppConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.value.PropertyCatalog;

import java.util.Map;

@ConfigurationProperties("service")
class AppConfig {

    private final Map<Country, CountryConfig> countries;
    private final Map<Country, Integer> populations;

    @ConfigurationInject
    AppConfig(
            Map<Country, CountryConfig> countries,
            @MapFormat(keyFormat = StringConvention.RAW) Map<Country, Integer> populations) {
        this.countries = countries;
        this.populations = populations;
    }

    public Map<Country, CountryConfig> getCountries() {
        return countries;
    }

    public Map<Country, Integer> getPopulations() {
        return populations;
    }
}

@EachProperty(value = "service.countries", catalog = PropertyCatalog.RAW)
class CountryConfig {

    private final int population;
    private final int area;

    @ConfigurationInject
    CountryConfig(int population, int area) {
        this.population = population;
        this.area = area;
    }

    public int getPopulation() {
        return population;
    }

    public int getArea() {
        return area;
    }
}

enum Country {
    France, UAE
}
''', true, [
                'service.countries.France.population': 69081996,
                'service.countries.France.area'      : 551695,
                'service.countries.UAE.population'   : 11027129,
                'service.countries.UAE.area'         : 83600,
                'service.populations.France'         : 69081996,
                'service.populations.UAE'            : 11027129
        ])
        Class<?> countryType = context.classLoader.loadClass('test.Country')
        def france = enumConstant(context, 'test.Country', 'France')
        def uae = enumConstant(context, 'test.Country', 'UAE')

        when: 'the configuration bean is resolved'
        def config = context.getBean(context.classLoader.loadClass('test.AppConfig'))

        then: '''the map is keyed by the enum and holds the @EachProperty beans.
                 Before the fix this failed with a NoSuchBeanException, because an enum-keyed Map was
                 never treated as an injectable map and fell through to a single-bean lookup.'''
        config.countries.size() == 2
        config.countries.keySet().every { countryType.isInstance(it) }
        config.countries[france].population == 69081996
        config.countries[france].area == 551695
        config.countries[uae].population == 11027129
        config.countries[uae].area == 83600

        and: 'the scalar map alongside it is bound as well, which is what the issue reports working'
        config.populations[france] == 69081996
        config.populations[uae] == 11027129

        cleanup:
        context.close()
    }

    void 'test an enum-keyed Map with a scalar value still binds as a configuration property'() {
        given: 'a configuration bean holding only an enum-keyed scalar map'
        ApplicationContext context = buildContext('test.ScalarConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.Map;

@ConfigurationProperties("service")
class ScalarConfig {

    private final Map<Country, Integer> populations;

    @ConfigurationInject
    ScalarConfig(@MapFormat(keyFormat = StringConvention.RAW) Map<Country, Integer> populations) {
        this.populations = populations;
    }

    public Map<Country, Integer> getPopulations() {
        return populations;
    }
}

enum Country {
    France, UAE
}
''', true, [
                'service.populations.France': 69081996,
                'service.populations.UAE'   : 11027129
        ])
        def france = enumConstant(context, 'test.Country', 'France')
        def uae = enumConstant(context, 'test.Country', 'UAE')

        when:
        def config = context.getBean(context.classLoader.loadClass('test.ScalarConfig'))

        then: 'the scalar map is bound from configuration, as it is without this change'
        config.populations.size() == 2
        config.populations[france] == 69081996
        config.populations[uae] == 11027129

        cleanup:
        context.close()
    }

    void 'test an enum-keyed Map of @EachBean beans is collected by bean name'() {
        given: 'one bean per configured shard'
        ApplicationContext context = buildContext('test.Registry', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.value.PropertyCatalog;
import jakarta.inject.Singleton;

import java.util.Map;

@EachProperty(value = "shards", catalog = PropertyCatalog.RAW)
class ShardConfig {

    private final int size;

    @ConfigurationInject
    ShardConfig(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}

@EachBean(ShardConfig.class)
class ShardClient {

    private final ShardConfig config;

    ShardClient(ShardConfig config) {
        this.config = config;
    }

    public int getSize() {
        return config.getSize();
    }
}

enum Shard {
    ALPHA, BETA
}

@Singleton
class Registry {

    private final Map<Shard, ShardClient> clients;

    Registry(Map<Shard, ShardClient> clients) {
        this.clients = clients;
    }

    public Map<Shard, ShardClient> getClients() {
        return clients;
    }
}
''', true, ['shards.alpha.size': 10, 'shards.beta.size': 20])
        def alpha = enumConstant(context, 'test.Shard', 'ALPHA')
        def beta = enumConstant(context, 'test.Shard', 'BETA')

        when:
        def registry = context.getBean(context.classLoader.loadClass('test.Registry'))

        then: '''a bean named after its parent bean is reachable by an enum key too, and the bean name
                 is matched by the usual lenient CharSequence to enum conversion rather than exactly.'''
        registry.clients.size() == 2
        registry.clients[alpha].size == 10
        registry.clients[beta].size == 20

        cleanup:
        context.close()
    }

    void 'test a @Factory-provided enum-keyed Map of plain beans is still resolved as a single bean'() {
        given: 'a factory that exposes an enum-keyed Map as a bean'
        ApplicationContext context = buildContext('test.Dispatcher', '''
package test;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

enum Status {
    OPEN, CLOSED
}

interface Handler {
    String handle();
}

@Factory
class HandlerFactory {

    @Singleton
    Map<Status, Handler> handlers() {
        Map<Status, Handler> handlers = new LinkedHashMap<>();
        handlers.put(Status.OPEN, () -> "open");
        handlers.put(Status.CLOSED, () -> "closed");
        return handlers;
    }
}

@Singleton
class Dispatcher {

    private final Map<Status, Handler> handlers;

    Dispatcher(Map<Status, Handler> handlers) {
        this.handlers = handlers;
    }

    public Map<Status, Handler> getHandlers() {
        return handlers;
    }
}
''', true, [:])
        def open = enumConstant(context, 'test.Status', 'OPEN')

        when:
        def dispatcher = context.getBean(context.classLoader.loadClass('test.Dispatcher'))

        then: 'the map produced by the factory is injected, not a map collected by bean name'
        dispatcher.handlers.size() == 2
        dispatcher.handlers[open].handle() == 'open'

        cleanup:
        context.close()
    }

    void 'test a @Factory-provided enum-keyed Map of iterable beans takes precedence over collecting them'() {
        given: 'a factory supplying the same map type the beans would be collected into'
        ApplicationContext context = buildContext('test.Consumer', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.value.PropertyCatalog;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

@EachProperty(value = "service.countries", catalog = PropertyCatalog.RAW)
class CountryConfig {

    private final int population;

    @ConfigurationInject
    CountryConfig(int population) {
        this.population = population;
    }

    public int getPopulation() {
        return population;
    }
}

enum Country {
    France
}

@Factory
class MapFactory {

    @Singleton
    Map<Country, CountryConfig> countries() {
        Map<Country, CountryConfig> countries = new LinkedHashMap<>();
        countries.put(Country.France, new CountryConfig(99));
        return countries;
    }
}

@Singleton
class Consumer {

    private final Map<Country, CountryConfig> countries;

    Consumer(Map<Country, CountryConfig> countries) {
        this.countries = countries;
    }

    public Map<Country, CountryConfig> getCountries() {
        return countries;
    }
}
''', true, ['service.countries.France.population': 1])
        def france = enumConstant(context, 'test.Country', 'France')

        when:
        def consumer = context.getBean(context.classLoader.loadClass('test.Consumer'))

        then: 'the factory map wins: 99 comes from the factory, 1 would mean the beans were collected'
        consumer.countries[france].population == 99

        cleanup:
        context.close()
    }

    private static Object enumConstant(ApplicationContext context, String enumType, String constant) {
        Enum.valueOf(context.classLoader.loadClass(enumType) as Class<? extends Enum>, constant)
    }
}
