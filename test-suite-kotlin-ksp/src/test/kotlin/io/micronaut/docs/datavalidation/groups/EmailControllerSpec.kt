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
package io.micronaut.docs.datavalidation.groups

import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

class EmailControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "datavalidationgroups"))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        //tag::pojovalidateddefault[]
        "test pojo validation using default validation groups" {
            val e = shouldThrow<HttpClientResponseException> {
                val email = Email()
                email.subject = ""
                email.recipient = ""
                client.toBlocking().exchange<Email, Any>(HttpRequest.POST("/email/createDraft", email))
            }
            var response = e.response

            response.status shouldBe HttpStatus.BAD_REQUEST

            val email = Email()
            email.subject = "Hi"
            email.recipient = ""
            response = client.toBlocking().exchange<Email, Any>(HttpRequest.POST("/email/createDraft", email))

            response.status shouldBe HttpStatus.OK
        }
        //end::pojovalidateddefault[]

        //tag::pojovalidatedfinal[]
        "test pojo validation using FinalValidation validation group" {
            val e = shouldThrow<HttpClientResponseException> {
                val email = Email()
                email.subject = "Hi"
                email.recipient = ""
                client.toBlocking().exchange<Email, Any>(HttpRequest.POST("/email/send", email))
            }
            var response = e.response

            response.status shouldBe HttpStatus.BAD_REQUEST

            val email = Email()
            email.subject = "Hi"
            email.recipient = "me@micronaut.example"
            response = client.toBlocking().exchange<Email, Any>(HttpRequest.POST("/email/send", email))

            response.status shouldBe HttpStatus.OK
        }
        //end::pojovalidatedfinal[]
    }
}
