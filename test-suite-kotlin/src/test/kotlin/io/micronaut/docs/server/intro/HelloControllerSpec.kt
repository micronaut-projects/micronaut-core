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
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// end::imports[]

// tag::class-init[]
class HelloControllerSpec() {
    lateinit var server: EmbeddedServer
    lateinit var client: HttpClient

    @BeforeTest
    fun setup() {
        // end::class-init[]
        server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to HelloControllerSpec::class.simpleName), Environment.TEST)

        /*
// tag::embeddedServer[]
        server = ApplicationContext.run(EmbeddedServer::class.java) // <1>
// end::embeddedServer[]
        */
        //tag::class[]
        client = server
                .getApplicationContext()
                .createBean(HttpClient::class.java, server.getURL())// <2>
    }

    @AfterTest
    fun teardown() {
        client?.close()
        server?.close()
    }

    @Test
    fun testHelloWorldResponse() {
        val rsp: String = client.toBlocking() // <3>
                .retrieve("/hello")
        assertEquals("Hello World", rsp) // <4>
    }
}
//end::class[]
