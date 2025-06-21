/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.docs.server.binding

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import java.util.Map

class PointControllerTest : StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java, Map.of<String, Any>("spec.name", "PointControllerTest"))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test JSON with no @Body endpoint"() {
            val httpRequest: HttpRequest<String> = HttpRequest
                .POST("/point/no-body-json", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            val response = client!!.toBlocking().exchange(httpRequest, Point::class.java)

            assertResult(response.body.orElse(null))
        }

        "test Form data with no @Body endpoint"() {
            val httpRequest: HttpRequest<String> = HttpRequest
                .POST("/point/no-body-form", "x=10&y=20")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
            val response = client!!.toBlocking().exchange(httpRequest, Point::class.java)

            assertResult(response.body.orElse(null))
        }
    }

    private fun assertResult(p: Point) {
        p shouldNotBe null
        p.x shouldBe 10
        p.y shouldBe 20
    }
}
