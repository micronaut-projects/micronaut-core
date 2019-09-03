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
package io.micronaut.docs.consumes

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

@Requires(property = "spec.name", value = "consumesspec")
//tag::clazz[]
@Controller("/test")
class TestController {

    @Consumes([MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON]) // <1>
    @Post("/multiple-consumes")
    HttpResponse multipleConsumes() {
        HttpResponse.ok()
    }

    @Post // <2>
    HttpResponse index() {
        HttpResponse.ok()
    }
}
//end::clazz[]