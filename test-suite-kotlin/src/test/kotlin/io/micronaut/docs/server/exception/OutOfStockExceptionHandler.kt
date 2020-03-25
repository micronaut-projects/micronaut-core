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
package io.micronaut.docs.server.exception

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler

import javax.inject.Singleton

//tag::clazz[]
@Produces
@Singleton
@Requirements(
//end::clazz[]
        Requires(property = "spec.name", value = "ExceptionHandlerSpec"),
//tag::clazz[]
        Requires(classes = [OutOfStockException::class, ExceptionHandler::class])
)
class OutOfStockExceptionHandler : ExceptionHandler<OutOfStockException, HttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: OutOfStockException): HttpResponse<*> {
        return HttpResponse.ok(0)
    }
}
//end::clazz[]
