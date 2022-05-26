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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import reactor.core.publisher.Mono
import spock.lang.Specification

import jakarta.inject.Inject
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
@Property(name = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@MicronautTest // <1>
class HelloClientSpec extends Specification {

    @Inject HelloClient client // <2>

    void "test hello world response"() {
        expect:
        Mono.from(client.hello()).block() == "Hello World" // <3>
    }

}
// end::class[]
