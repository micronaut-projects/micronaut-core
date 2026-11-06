/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.defaults

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A controller implementing an interface whose route methods declare default arguments: the
 * synthetic `$default` method the defaults are applied by belongs to the interface, not to the
 * controller. See https://github.com/micronaut-projects/micronaut-core/issues/12638.
 */
@MicronautTest
@Property(name = "spec.name", value = "InterfaceDefaultArgumentsTest")
class InterfaceDefaultArgumentsTest {

    @Inject
    lateinit var server: EmbeddedServer

    @Test
    fun `arguments given in the request are passed to the route`() {
        assertEquals("one-two", retrieve("/interface-defaults?first=one&second=two"))
    }

    @Test
    fun `an absent argument gets the default declared by the interface`() {
        assertEquals("default-null", retrieve("/interface-defaults"))
    }

    private fun retrieve(uri: String): String =
        server.applicationContext.createBean(HttpClient::class.java, server.uri).use {
            it.toBlocking().retrieve(uri)
        }

    interface MyApi {
        @Get
        fun send(
            @QueryValue("first") first: String = "default",
            @QueryValue("second") second: String? = null
        ): String
    }

    @Requires(property = "spec.name", value = "InterfaceDefaultArgumentsTest")
    @Controller("/interface-defaults")
    open class MyController : MyApi {
        override fun send(first: String, second: String?): String = "$first-$second"
    }
}
