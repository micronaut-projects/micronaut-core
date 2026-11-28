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
package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.BulkheadRegistry
import io.micronaut.retry.exception.BulkheadFullException
import io.micronaut.retry.exception.TimeLimitExceededException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BulkheadTimeLimitSpec {

    @Test
    fun testBulkheadRefusesACallBeyondItsLimit() {
        ApplicationContext.run().use { context ->
            val service = context.getBean(ReportService::class.java)

            val first = service.render("first")
            service.render("second")
            val third = service.render("third")

            val e = assertThrows(ExecutionException::class.java) { third.get(5, TimeUnit.SECONDS) }
            assertInstanceOf(BulkheadFullException::class.java, e.cause)
            assertFalse(service.isStarted("third"))

            // a call that ends gives its place to the next
            service.finish("first")
            assertEquals("first done", first.get(5, TimeUnit.SECONDS))
            service.render("fourth")
            assertTrue(service.isStarted("fourth"))
        }
    }

    @Test
    fun testMethodsOfOneNameShareThePlacesAndWaitForOne() {
        ApplicationContext.run().use { context ->
            val service = context.getBean(ReportService::class.java)
            val registry = context.getBean(BulkheadRegistry::class.java)

            val stored = service.store("stored")
            val loaded = service.load("loaded")

            // the second waits for the one place of the bulkhead
            assertFalse(service.isStarted("loaded"))
            assertEquals(1, registry.findSnapshot("archive").orElseThrow().active())
            assertEquals(1, registry.findSnapshot("archive").orElseThrow().waiting())

            service.finish("stored")
            assertEquals("stored done", stored.get(5, TimeUnit.SECONDS))
            assertTrue(service.isStarted("loaded"))
            service.finish("loaded")
            assertEquals("loaded done", loaded.get(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun testTimeLimitFailsACallThatTakesTooLong() {
        ApplicationContext.run().use { context ->
            val service = context.getBean(ReportService::class.java)

            runBlocking {
                assertFailsWith<TimeLimitExceededException> { service.export("late") }

                val inTime = async { service.export("in time") }
                while (!service.isStarted("in time")) {
                    delay(10)
                }
                service.finish("in time")
                assertEquals("in time done", inTime.await())
            }
        }
    }
}
