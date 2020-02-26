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
package io.micronaut.docs.httpclientexceptionbody

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import java.util.HashMap

@Requires(property = "spec.name", value = "BindHttpClientExceptionBodySpec")
//tag::clazz[]
@Controller("/books")
class BooksController {

    @Get("/{isbn}")
    fun find(isbn: String): HttpResponse<*> {
        if (isbn == "1680502395") {
            val m = HashMap<String, Any>()
            m["status"] = 401
            m["error"] = "Unauthorized"
            m["message"] = "No message available"
            m["path"] = "/books/$isbn"
            return HttpResponse.status<Any>(HttpStatus.UNAUTHORIZED).body(m)

        }
        return HttpResponse.ok(Book("1491950358", "Building Microservices"))
    }
}
//end::clazz[]
