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
package io.micronaut.docs.server.json

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.jupiter.api.Assertions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PersonControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to PersonControllerSpec::class.simpleName))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test global error handler"() {
            val e = Assertions.assertThrows(HttpClientResponseException::class.java) {
                client!!.exchange("/people/error", Map::class.java)
                        .blockingFirst()
            }
            val response = e.response as HttpResponse<Map<*, *>>

            response.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            response.body.get()["message"] shouldBe "Bad Things Happened: Something went wrong"
        }

        "test save"() {
            var response = client.exchange(HttpRequest.POST("/people", "{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}"), Person::class.java).blockingFirst()
            var person = response.body.get()

            person.firstName shouldBe "Fred"
            response.status shouldBe HttpStatus.CREATED

            response = client.exchange(HttpRequest.GET<Any>("/people/Fred"), Person::class.java).blockingFirst()
            person = response.body.get()

            person.firstName shouldBe "Fred"
            response.status shouldBe HttpStatus.OK
        }

        "test save reactive"() {
            val response = client.exchange(HttpRequest.POST("/people/saveReactive", "{\"firstName\":\"Wilma\",\"lastName\":\"Flintstone\",\"age\":36}"), Person::class.java).blockingFirst()
            val person = response.body.get()

            person.firstName shouldBe "Wilma"
            response.status shouldBe HttpStatus.CREATED
        }

        "test save future"() {
            val response = client!!.exchange(HttpRequest.POST("/people/saveFuture", "{\"firstName\":\"Pebbles\",\"lastName\":\"Flintstone\",\"age\":0}"), Person::class.java).blockingFirst()
            val person = response.body.get()

            person.firstName shouldBe "Pebbles"
            response.status shouldBe HttpStatus.CREATED
        }

        "test save args"() {
            val response = client!!.exchange(HttpRequest.POST("/people/saveWithArgs", "{\"firstName\":\"Dino\",\"lastName\":\"Flintstone\",\"age\":3}"), Person::class.java).blockingFirst()
            val person = response.body.get()

            person.firstName shouldBe "Dino"
            response.status shouldBe HttpStatus.CREATED
        }

        "test person not found"() {
            val e = shouldThrow<HttpClientResponseException> {
                client.exchange("/people/Sally", Map::class.java)
                        .blockingFirst()
            }
            val response = e.response as HttpResponse<Map<*, *>>

            response.body.get()["message"] shouldBe "Person Not Found"
            response.status shouldBe HttpStatus.NOT_FOUND
        }

        "test save invalid json"() {
            val e = shouldThrow<HttpClientResponseException> {
                client.exchange<String, Person, Map<*, *>>(HttpRequest.POST("/people", "{\""), Argument.of(Person::class.java), Argument.of(Map::class.java)).blockingFirst()
            }
            val response = e.response as HttpResponse<Map<*, *>>

            assertTrue(response.getBody(Map::class.java).get()["message"].toString().startsWith("Invalid JSON: Unexpected end-of-input"))
            response.status shouldBe HttpStatus.BAD_REQUEST
        }
    }

}
