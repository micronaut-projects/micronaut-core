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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders.*
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.HttpRequest.OPTIONS
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.reactive.awaitSingle
import java.io.IOException

class SuspendRepositorySpec : StringSpec() {

    val context = autoClose(
        ApplicationContext.run()
    )

    private var suspendRepository = context.getBean(SuspendRepository::class.java)

    init {
        "test exception unwrapped" {
            shouldThrow<IOException> {
                suspendRepository.get()
            }
        }
    }
}
