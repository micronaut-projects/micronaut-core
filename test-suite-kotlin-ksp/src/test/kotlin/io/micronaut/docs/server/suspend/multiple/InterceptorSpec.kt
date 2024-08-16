/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.suspend.multiple

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import kotlinx.coroutines.runBlocking

class InterceptorSpec : StringSpec() {

    val context = autoClose(
        ApplicationContext.run()
    )

    private var myService = context.getBean(MyService::class.java)

    private var repository = context.getBean(CustomRepository::class.java)

    init {
        "test correct interceptors calls" {
            runBlocking {
                MyService.events.clear()
                myService.someCall()
                MyService.events.size shouldBeExactly 8
                MyService.events[0] shouldBe "intercept1-start"
                MyService.events[1] shouldBe "intercept2-start"
                MyService.events[2] shouldBe "repository-abc"
                MyService.events[3] shouldBe "repository-xyz"
                MyService.events[4] shouldBe "intercept2-end"
                MyService.events[5] shouldBe "intercept1-end"
                MyService.events[6] shouldBe "repository-count1"
                MyService.events[7] shouldBe "repository-count2"
            }
        }

        "test calling generic method" {
            runBlocking {
                MyService.events.clear()
                // Validate that no bytecode error is produced
                repository.findById(111)
                MyService.events.size shouldBeExactly 1
                MyService.events[0] shouldBe "repository-findById"
            }
        }

        "test calling delete method" {
            runBlocking {
                MyService.events.clear()
                // Validate that no bytecode error is produced
                repository.deleteById(111)
                MyService.events.size shouldBeExactly 1
                MyService.events[0] shouldBe "repository-deleteById"
            }
        }
    }
}
