/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.web.router.version

// tag::imports[]
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]

// tag::clazz[]
@Controller("/versioned")
class VersionedController {

    @Version("1") // <1>
    @Get("/hello")
    String helloV1() {
        "helloV1"
    }

    @Version("2") // <2>
    @Get("/hello")
    String helloV2() {
        "helloV2"
    }
// end::clazz[]

    @Version("2")
    @Get("/hello")
    String duplicatedHelloV2() {
        "duplicatedHelloV2"
    }

    @Get("/hello")
    String hello() {
        "hello"
    }
}
