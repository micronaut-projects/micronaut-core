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
package io.micronaut.docs.server.response

//tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
//end::imports[]

@Requires(property = 'spec.name', value = 'producesspec')
//tag::clazz[]
@Controller("/produces")
class ProducesController {

    @Get // <1>
    HttpResponse index() {
        HttpResponse.ok().body("{\"msg\":\"This is JSON\"}")
    }

    @Produces(MediaType.TEXT_HTML) // <2>
    @Get("/html")
    String html() {
        "<html><title><h1>HTML</h1></title><body></body></html>"
    }

    @Get(value = "/xml", produces = MediaType.TEXT_XML) // <3>
    String xml() {
        return "<html><title><h1>XML</h1></title><body></body></html>"
    }
}
//end::clazz[]
