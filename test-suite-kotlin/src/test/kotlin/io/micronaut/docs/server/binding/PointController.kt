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

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status

@Requires(property = "spec.name", value = "PointControllerTest")
// tag::class[]
@Controller("/point")
class PointController {

    @Post(uri = "/no-body-json")
    @Status(HttpStatus.CREATED)
    fun noBodyJson(x: Int, y: Int) = Point(x,y) // (1)

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/no-body-form")
    @Status(HttpStatus.CREATED)
    fun noBodyForm(x: Int, y: Int) = Point(x,y)  // (2)
}
// end::class[]
