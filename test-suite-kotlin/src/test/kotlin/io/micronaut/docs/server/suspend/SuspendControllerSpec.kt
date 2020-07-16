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

import io.kotlintest.*
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders.*
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.HttpRequest.OPTIONS
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.opentest4j.AssertionFailedError
import java.lang.IllegalArgumentException

class SuspendControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf(
                    "micronaut.server.cors.enabled" to true,
                    "micronaut.server.cors.configurations.dev.allowedOrigins" to listOf("foo.com"),
                    "micronaut.server.cors.configurations.dev.allowedMethods" to listOf("GET"),
                    "micronaut.server.cors.configurations.dev.allowedHeaders" to listOf(ACCEPT, CONTENT_TYPE)
            ))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test suspend applies CORS options"() {
            val origin = "foo.com"
            val headers = "$CONTENT_TYPE,$ACCEPT"
            val method = HttpMethod.GET
            val optionsResponse = client.exchange(
                    OPTIONS<Any>("/suspend/greet")
                            .header(ORIGIN, origin)
                            .header(ACCESS_CONTROL_REQUEST_METHOD, method)
                            .header(ACCESS_CONTROL_REQUEST_HEADERS, headers)
            ).blockingFirst()


            optionsResponse.status shouldBe HttpStatus.OK
            optionsResponse.header(ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe origin
            optionsResponse.header(ACCESS_CONTROL_ALLOW_METHODS) shouldBe method.toString()
            optionsResponse.headers.getAll(ACCESS_CONTROL_ALLOW_HEADERS).joinToString(",") shouldBe headers

            val response = client.exchange(
                    GET<String>("/suspend/greet?name=Fred")
                            .header(ORIGIN, origin)
            ).blockingFirst()

            response.status shouldBe HttpStatus.OK
            response.header(ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe origin

        }

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

        "test suspend invoked once"() {
            val response = client.exchange(HttpRequest.GET<Any>("/suspend/count"), Integer::class.java).blockingFirst()
            val body = response.body.get()

            body shouldBe 1
            response.status shouldBe HttpStatus.OK
        }

        "test error route"() {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                client.exchange(HttpRequest.GET<Any>("/suspend/illegal"), String::class.java).blockingFirst()
            }
            val body = ex.response.getBody(String::class.java).get()

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
        }

        "test suspend functions that throw exceptions inside withContext emit an error response to filters"() {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                client.exchange(HttpRequest.GET<Any>("/suspend/illegalWithContext"), String::class.java).blockingFirst()
            }
            val body = ex.response.getBody(String::class.java).get()
            val filter = embeddedServer.applicationContext.getBean(SuspendFilter::class.java)

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
            filter.response shouldBe null
            filter.error should { t -> t is IllegalArgumentException }

        }
    }
}