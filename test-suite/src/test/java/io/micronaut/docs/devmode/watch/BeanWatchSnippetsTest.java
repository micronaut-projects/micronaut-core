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
package io.micronaut.docs.devmode.watch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ReloadingConfigurationWatcher.Outcome;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanWatchSnippetsTest {

    private static final Map<String, Object> PROPERTIES = Map.of(
        "spec.name", "BeanWatchSnippetsTest",
        "pools.default.url", "jdbc:h2:mem:one",
        "pools.default.username", "sa",
        "pools.default.password", "one",
        DevelopmentMode.PROPERTY, true
    );

    private static ClassChangeEvent retiringThisLoader() {
        // every class of the test's loader is a class of the retired generation
        return new ClassChangeEvent(BeanWatchSnippetsTest.class, Set.of(BeanWatchSnippetsTest.class.getClassLoader()),
            BeanWatchSnippetsTest.class.getClassLoader(),
            List.of(new ClassChange(StringSerializer.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RESTART);
    }

    @Test
    void theRouteTableFollowsTheRouteBuildersRegistered() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).start()) {
            RouteTable table = context.getBean(RouteTable.class);
            int before = table.builders().size();

            context.registerBeanDefinition(RuntimeBeanDefinition.builder(RouteBuilder.class, () -> {
                throw new UnsupportedOperationException("never created: the watch reads definitions only");
            }).singleton(true).build());

            assertEquals(before + 1, table.builders().size());
        }
    }

    @Test
    void theCodecHandlersSeeEachAddition() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).start()) {
            CodecRegistry registry = context.getBean(CodecRegistry.class);
            assertEquals(Set.of(JsonCodec.class), registry.codecs().stream().map(definition -> definition.getBeanType()).collect(java.util.stream.Collectors.toSet()));

            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Codec.class, () -> () -> "text/plain").named("text").singleton(true).build());

            assertEquals(2, registry.codecs().size());
        }
    }

    @Test
    void thePoolAppliesNewCredentialsAndIsRecreatedForANewUrl() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).start()) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            ConnectionPool pool = context.getBean(ConnectionPool.class, Qualifiers.byName("default"));

            assertEquals(List.of(Outcome.APPLIED), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pools.default.password"))));
            assertEquals(1, pool.getEvictions());
            assertSame(pool, context.getBean(ConnectionPool.class, Qualifiers.byName("default")));

            assertEquals(List.of(Outcome.IGNORED), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pools.default.maximum-pool-size"))));

            assertEquals(List.of(Outcome.RECREATE), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pools.default.url"))));
            assertNotSame(pool, context.getBean(ConnectionPool.class, Qualifiers.byName("default")));
        }
    }

    @Test
    void theSerializerRegistryBuildsItsStateFromTheFirstBatch() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).start()) {
            SerializerRegistry registry = context.getBean(SerializerRegistry.class);
            assertEquals(List.of(StringSerializer.class), registry.serializers().stream().map(definition -> definition.getBeanType()).toList());
        }
    }

    @Test
    void theCacheForgetsTheClassesAReloadRetires() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).start()) {
            TypeDescriptionCache cache = context.getBean(TypeDescriptionCache.class);
            cache.describe(StringSerializer.class);
            cache.describe(String.class);

            context.publishEvent(retiringThisLoader());

            // String comes from the JDK, not from the retired generation
            assertEquals(1, cache.size());
        }
    }

    @Test
    void theLookupAndItsDependentsAreRecreatedWhenASerializerClassIsRetired() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).start()) {
            Writer writer = context.getBean(Writer.class);
            SerializerLookup lookup = writer.lookup();
            assertEquals("\"a\"", writer.write("a"));

            context.publishEvent(retiringThisLoader());

            Writer recreated = context.getBean(Writer.class);
            assertNotSame(writer, recreated);
            assertNotSame(lookup, recreated.lookup());
            assertSame(recreated.lookup(), context.getBean(SerializerLookup.class));
            assertFalse(recreated.write("b").isEmpty());
            assertTrue(context.getBean(SerializerLookup.class) != lookup);
        }
    }
}
