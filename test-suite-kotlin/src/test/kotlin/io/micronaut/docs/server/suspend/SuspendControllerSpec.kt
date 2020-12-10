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

import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrowExactly
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders.*
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.HttpRequest.OPTIONS
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.reactive.awaitSingle

class SuspendControllerSpec : StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(
            EmbeddedServer::class.java, mapOf(
                "micronaut.server.cors.enabled" to true,
                "micronaut.server.cors.configurations.dev.allowedOrigins" to listOf("foo.com"),
                "micronaut.server.cors.configurations.dev.allowedMethods" to listOf("GET"),
                "micronaut.server.cors.configurations.dev.allowedHeaders" to listOf(ACCEPT, CONTENT_TYPE)
            )
        )
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )

    private var suspendClient = embeddedServer.applicationContext.createBean(SuspendClient::class.java, embeddedServer.url)

    init {
        "test suspend applies CORS options" {
            val origin = "foo.com"
            val headers = "$CONTENT_TYPE,$ACCEPT"
            val method = HttpMethod.GET
            val optionsResponse = client.exchange(
                OPTIONS<Any>("/suspend/greet")
                    .header(ORIGIN, origin)
                    .header(ACCESS_CONTROL_REQUEST_METHOD, method)
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, headers)
            ).awaitSingle()

            optionsResponse.status shouldBe HttpStatus.OK
            optionsResponse.header(ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe origin
            optionsResponse.header(ACCESS_CONTROL_ALLOW_METHODS) shouldBe method.toString()
            optionsResponse.headers.getAll(ACCESS_CONTROL_ALLOW_HEADERS).joinToString(",") shouldBe headers

            val response = client.exchange(
                GET<String>("/suspend/greet?name=Fred")
                    .header(ORIGIN, origin)
            ).awaitSingle()

            response.status shouldBe HttpStatus.OK
            response.header(ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe origin
        }

        "test suspend service with retries" {
            val response = client.exchange(GET<Any>("/suspend/callSuspendServiceWithRetries"), String::class.java).awaitSingle()
            val body = response.body.get()

            body shouldBe "delayedCalculation1"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend service with retries blocked" {
            val response = client.exchange(GET<Any>("/suspend/callSuspendServiceWithRetriesBlocked"), String::class.java).awaitSingle()
            val body = response.body.get()

            body shouldBe "delayedCalculation2"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend service with retries without delay" {
            val response = client.exchange(GET<Any>("/suspend/callSuspendServiceWithRetriesWithoutDelay"), String::class.java).awaitSingle()
            val body = response.body.get()

            body shouldBe "delayedCalculation3"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend" {
            val response = client.exchange(GET<Any>("/suspend/simple"), String::class.java).awaitSingle()
            val body = response.body.get()

            body shouldBe "Hello"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend calling client" {
            val body = suspendClient.simple()

            body shouldBe "Hello"
        }

        "test suspend calling client ignore result" {
            suspendClient.simpleIgnoreResult()
            // No exception thrown
        }

        "test suspend calling client method with response return" {
            val response = suspendClient.simpleResponse()
            val body = response.body.get()

            body shouldBe "Hello"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend calling client method with response return ignore result" {
            val response = suspendClient.simpleResponse()
            val body = response.body.get()

            body shouldBe "Hello"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend delayed" {
            val response = client.exchange(GET<Any>("/suspend/delayed"), String::class.java).awaitSingle()
            val body = response.body.get()

            body shouldBe "Delayed"
            response.status shouldBe HttpStatus.OK
        }

        "test suspend status" {
            val response = client.exchange(GET<Any>("/suspend/status"), String::class.java).awaitSingle()

            response.status shouldBe HttpStatus.CREATED
        }

        "test suspend status delayed" {
            val response = client.exchange(GET<Any>("/suspend/statusDelayed"), String::class.java).awaitSingle()

            response.status shouldBe HttpStatus.CREATED
        }

        "test suspend invoked once" {
            val response = client.exchange(GET<Any>("/suspend/count"), Integer::class.java).awaitSingle()
            val body = response.body.get()

            body shouldBe 1
            response.status shouldBe HttpStatus.OK
        }

        "test error route" {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                client.exchange(GET<Any>("/suspend/illegal"), String::class.java).awaitSingle()
            }
            val body = ex.response.getBody(String::class.java).get()

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
        }

        "test error route with client response" {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                suspendClient.errorCallResponse()
            }
            val body = ex.response.getBody(String::class.java).get()

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
        }

        "test error route with client string response" {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                suspendClient.errorCall()
            }
            val body = ex.response.getBody(String::class.java).get()

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
        }

        "test suspend functions that throw exceptions inside withContext emit an error response to filters" {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                client.exchange(GET<Any>("/suspend/illegalWithContext"), String::class.java).awaitSingle()
            }
            val body = ex.response.getBody(String::class.java).get()
            val filter = embeddedServer.applicationContext.getBean(SuspendFilter::class.java)

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
            filter.response shouldBe null
            filter.error should { t -> t is IllegalArgumentException }
        }

        "test keeping request scope inside coroutine" {
            val response = client.exchange(GET<Any>("/suspend/keepRequestScopeInsideCoroutine"), String::class.java).awaitSingle()
            val body = response.body.get()

            val (beforeRequestId, beforeThreadId, afterRequestId, afterThreadId) = body.split(',')
            beforeRequestId shouldBe afterRequestId
            beforeThreadId shouldNotBe afterThreadId
            response.status shouldBe HttpStatus.OK
        }

        "test keeping request scope inside coroutins with retry" {
            val response = client.exchange(GET<Any>("/suspend/keepRequestScopeInsideCoroutineWithRetry"), String::class.java).awaitSingle()
            val body = response.body.get()

            val (beforeRequestId, beforeThreadId, afterRequestId, afterThreadId) = body.split(',')
            beforeRequestId shouldBe afterRequestId
            beforeThreadId shouldNotBe afterThreadId
            response.status shouldBe HttpStatus.OK
        }
    }
}