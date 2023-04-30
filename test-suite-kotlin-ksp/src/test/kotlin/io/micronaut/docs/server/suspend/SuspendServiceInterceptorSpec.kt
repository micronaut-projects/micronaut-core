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
package io.micronaut.docs.server.suspend

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class SuspendServiceInterceptorSpec : StringSpec() {

    val context = autoClose(
            ApplicationContext.run()
    )

    private var suspendService = context.getBean(SuspendService::class.java)

    init {
        "should append to context " {
            coroutineContext[MyContext] shouldBe null

            suspendService.call1() shouldBe "call1"
            suspendService.call2() shouldBe "call2"
            suspendService.call3() shouldBe "call1"
            suspendService.call4() shouldBe "call4"
        }
    }
}
