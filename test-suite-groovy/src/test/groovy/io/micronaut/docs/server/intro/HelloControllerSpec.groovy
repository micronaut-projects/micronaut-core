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

import io.micronaut.context.annotation.Property


// tag::imports[]
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import javax.inject.Inject
// end::imports[]
/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Property(name = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@MicronautTest
class HelloControllerSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer // <1>

    @Inject
    @Client("/")
    HttpClient client // <2>

    void "test hello world response"() {
        expect:
            client.toBlocking() // <3>
                    .retrieve(HttpRequest.GET('/hello')) == "Hello World" // <4>
    }
}
// end::class[]
