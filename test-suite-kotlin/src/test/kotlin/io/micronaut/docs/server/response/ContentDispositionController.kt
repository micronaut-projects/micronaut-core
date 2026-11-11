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
package io.micronaut.docs.server.response

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.ContentDisposition
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = "spec.name", value = "contentdisposition")
@Controller("/content-disposition")
class ContentDispositionController {

    //tag::attachment[]
    @ContentDisposition(filename = "report.csv")
    @Get(value = "/report", produces = [MediaType.TEXT_PLAIN])
    fun report(): String {
        return "name,amount\nwidget,42"
    }
    //end::attachment[]

    //tag::inline[]
    @ContentDisposition(type = ContentDisposition.Type.INLINE)
    @Get(value = "/preview", produces = [MediaType.TEXT_PLAIN])
    fun preview(): String {
        return "This is displayed rather than downloaded"
    }
    //end::inline[]
}
