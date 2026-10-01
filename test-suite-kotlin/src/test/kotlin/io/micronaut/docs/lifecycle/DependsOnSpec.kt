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
package io.micronaut.docs.lifecycle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class DependsOnSpec : StringSpec({

    "test @DependsOn orders creation and destruction" {
        ShutdownLog.clear()
        val ctx = ApplicationContext.run()
        ctx.getBean(MessageConsumer::class.java)

        ShutdownLog.events() shouldBe listOf("publisher created", "consumer created")

        ctx.stop()

        ShutdownLog.events() shouldBe listOf("publisher created", "consumer created", "consumer stopped", "publisher closed")
    }
})
