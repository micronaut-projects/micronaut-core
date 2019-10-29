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
package io.micronaut.docs.server.intro

// tag::imports[]
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.inject.Inject
// end::imports[]
@Property(name = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@MicronautTest
class HelloControllerSpec {

    @Inject
    lateinit var server: EmbeddedServer // <1>

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient // <2>

    @Test
    fun testHelloWorldResponse() {
        val rsp: String = client.toBlocking() // <3>
                .retrieve("/hello")
        assertEquals("Hello World", rsp) // <4>
    }
}
//end::class[]
