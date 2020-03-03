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

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer

class SuspendControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test suspend"() {
            val response = client.exchange(HttpRequest.GET<Any>("/suspend/simple"), String::class.java).blockingFirst()
            val body = response.body.get()

            body shouldBe "Hello"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend delayed"() {
            val response = client.exchange(HttpRequest.GET<Any>("/suspend/delayed"), String::class.java).blockingFirst()
            val body = response.body.get()

            body shouldBe "Delayed"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend status"() {
            val response = client.exchange(HttpRequest.GET<Any>("/suspend/status"), String::class.java).blockingFirst()

            response.status shouldBe HttpStatus.CREATED
        }

        "test suspend status delayed"() {
            val response = client.exchange(HttpRequest.GET<Any>("/suspend/statusDelayed"), String::class.java).blockingFirst()

            response.status shouldBe HttpStatus.CREATED
        }
    }
}