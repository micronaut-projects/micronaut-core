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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.function.Supplier

class BeanWatchSnippetsTest {

    private fun start(): ApplicationContext = ApplicationContext.builder(
        mapOf(
            "spec.name" to "BeanWatchSnippetsTest",
            "pools.default.url" to "jdbc:h2:mem:one",
            "pools.default.username" to "sa",
            "pools.default.password" to "one",
            DevelopmentMode.PROPERTY to true
        )
    ).start()

    private fun retiringThisLoader(): ClassChangeEvent {
        // every class of the test's loader is a class of the retired generation
        val loader = BeanWatchSnippetsTest::class.java.classLoader
        return ClassChangeEvent(
            BeanWatchSnippetsTest::class.java, setOf(loader), loader,
            listOf(ClassChange(StringSerializer::class.java.name, ClassChange.Kind.MODIFIED)), ReloadStrategy.RESTART
        )
    }

    @Test
    fun theRouteTableFollowsTheRouteBuildersRegistered() {
        start().use { context ->
            val table = context.getBean(RouteTable::class.java)
            val before = table.builders.size

            context.registerBeanDefinition(
                RuntimeBeanDefinition.builder(RouteBuilder::class.java, Supplier<RouteBuilder> { throw UnsupportedOperationException() }).singleton(true).build()
            )

            assertEquals(before + 1, table.builders.size)
        }
    }

    @Test
    fun theCodecHandlersSeeEachAddition() {
        start().use { context ->
            val registry = context.getBean(CodecRegistry::class.java)
            assertEquals(setOf(JsonCodec::class.java), registry.codecs.map { it.beanType }.toSet())

            context.registerBeanDefinition(
                RuntimeBeanDefinition.builder(Codec::class.java, Supplier<Codec> { Codec { "text/plain" } }).named("text").singleton(true).build()
            )

            assertEquals(2, registry.codecs.size)
        }
    }

    @Test
    fun thePoolAppliesNewCredentialsAndIsRecreatedForANewUrl() {
        start().use { context ->
            val beanContext = context as DefaultBeanContext
            val pool = context.getBean(ConnectionPool::class.java, Qualifiers.byName("default"))

            assertEquals(listOf(Outcome.APPLIED), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(setOf("pools.default.password"))))
            assertEquals(1, pool.evictions)
            assertSame(pool, context.getBean(ConnectionPool::class.java, Qualifiers.byName("default")))

            assertEquals(listOf(Outcome.IGNORED), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(setOf("pools.default.maximum-pool-size"))))

            assertEquals(listOf(Outcome.RECREATE), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(setOf("pools.default.url"))))
            assertNotSame(pool, context.getBean(ConnectionPool::class.java, Qualifiers.byName("default")))
        }
    }

    @Test
    fun theSerializerRegistryBuildsItsStateFromTheFirstBatch() {
        start().use { context ->
            val registry = context.getBean(SerializerRegistry::class.java)
            assertEquals(listOf(StringSerializer::class.java), registry.serializers.map { it.beanType })
        }
    }

    @Test
    fun theCacheForgetsTheClassesAReloadRetires() {
        start().use { context ->
            val cache = context.getBean(TypeDescriptionCache::class.java)
            cache.describe(StringSerializer::class.java)
            cache.describe(String::class.java)

            context.publishEvent(retiringThisLoader())

            // String comes from the JDK, not from the retired generation
            assertEquals(1, cache.size)
        }
    }

    @Test
    fun theLookupAndItsDependentsAreRecreatedWhenASerializerClassIsRetired() {
        start().use { context ->
            val writer = context.getBean(Writer::class.java)
            val lookup = writer.lookup
            assertEquals("\"a\"", writer.write("a"))

            context.publishEvent(retiringThisLoader())

            val recreated = context.getBean(Writer::class.java)
            assertNotSame(writer, recreated)
            assertNotSame(lookup, recreated.lookup)
            assertSame(recreated.lookup, context.getBean(SerializerLookup::class.java))
            assertEquals("\"b\"", recreated.write("b"))
        }
    }
}
