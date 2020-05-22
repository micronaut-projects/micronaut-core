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
package io.micronaut.docs.server.binding

// tag::imports[]
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import javax.annotation.Nullable
import javax.validation.constraints.PositiveOrZero
// end::imports[]

// tag::class[]
@Introspected
data class MovieTicketBean(val httpRequest: HttpRequest<Any>,
                           @field:PathVariable val movieId: String,
                           @field:QueryValue @field:PositiveOrZero @field:Nullable val minPrice: Double,
                           @field:QueryValue @field:PositiveOrZero @field:Nullable val maxPrice: Double)
// end::class[]