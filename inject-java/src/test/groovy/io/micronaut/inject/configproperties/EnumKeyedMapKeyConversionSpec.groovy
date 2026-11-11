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
import io.micronaut.context.exceptions.DependencyInjectionException

/**
 * Only the bean names are converted to the declared key type. The resolved beans are injected as they
 * are, and a name that cannot be converted fails the injection rather than quietly leaving a bean out.
 */
class EnumKeyedMapKeyConversionSpec extends AbstractTypeElementSpec {

    static String shardSource(String enumConstants) {
        """
package test;

import io.micronaut.context.annotation.ConfigurationInject;
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

enum Shard {
    ${enumConstants}
}

@Singleton
class Registry {

    private final Map<Shard, ShardConfig> shards;

    Registry(Map<Shard, ShardConfig> shards) {
        this.shards = shards;
    }

    public Map<Shard, ShardConfig> getShards() {
        return shards;
    }
}
"""
    }

    void 'test a bean whose name has no matching enum constant fails the injection'() {
        given: 'a shard configured under a key the enum does not have'
        ApplicationContext context = buildContext('test.Registry', shardSource('ALPHA, BETA'), true,
                ['shards.alpha.size': 10, 'shards.typo.size': 20])

        when:
        context.getBean(context.classLoader.loadClass('test.Registry'))

        then: 'the bean is not silently left out of the map'
        def e = thrown(DependencyInjectionException)
        e.message.contains('Cannot convert the name of bean [typo]')

        cleanup:
        context.close()
    }

    void 'test two bean names converting to the same enum constant fail the injection'() {
        given: 'two shards whose names both resolve to ALPHA'
        ApplicationContext context = buildContext('test.Registry', shardSource('ALPHA'), true,
                ['shards.alpha.size': 10, 'shards.ALPHA.size': 20])

        when:
        context.getBean(context.classLoader.loadClass('test.Registry'))

        then: 'one bean does not silently overwrite the other'
        def e = thrown(DependencyInjectionException)
        e.message.contains('More than one bean is named for the key [ALPHA]')

        cleanup:
        context.close()
    }

    void 'test a bean value that is itself a Map is injected as the bean, not a copy of it'() {
        given: 'an iterable bean type that extends LinkedHashMap'
        ApplicationContext context = buildContext('test.Holder', '''
package test;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.value.PropertyCatalog;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
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
class ShardMap extends LinkedHashMap<String, String> {

    ShardMap(ShardConfig config) {
        put("size", String.valueOf(config.getSize()));
    }
}

enum Shard {
    ALPHA
}

@Singleton
class Holder {

    private final Map<Shard, ShardMap> maps;

    Holder(Map<Shard, ShardMap> maps) {
        this.maps = maps;
    }

    public Map<Shard, ShardMap> getMaps() {
        return maps;
    }
}
''', true, ['shards.alpha.size': 10])
        Class<?> shardMapType = context.classLoader.loadClass('test.ShardMap')

        when:
        def holder = context.getBean(context.classLoader.loadClass('test.Holder'))
        def value = holder.maps.values().iterator().next()

        then: 'the managed bean is the value, rather than a plain map rebuilt from its entries'
        shardMapType.isInstance(value)
        value['size'] == '10'

        cleanup:
        context.close()
    }
}
