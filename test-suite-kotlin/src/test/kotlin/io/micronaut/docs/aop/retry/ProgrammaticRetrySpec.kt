/*
 * Copyright 2017-2020 original authors
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
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

class ProgrammaticRetrySpec {

    @Test
    fun testProgrammaticRetryExamples() {
        ApplicationContext.run().use { context ->
            val service = context.getBean(ProgrammaticBookService::class.java)

            service.reset()
            assertEquals("The Stand", service.listBooks().first().title)

            service.reset()
            assertEquals("The Stand", Mono.from(service.streamBooks()).block()!!.title)

            service.reset()
            assertEquals("The Stand", service.findBook("The Stand").toCompletableFuture().get().title)

            service.reset()
            assertEquals("The Stand", service.findBookWithCircuitBreaker("The Stand").title)
        }
    }
}
