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
import javax.validation.constraints.Pattern
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

// end::imports[]

/**
 * @author Puneet Behl
 * @since 1.0
 */
// tag::class[]
@Introspected
class PaginationCommand {
    // end::class[]

    // tag::props[]
    @PositiveOrZero
    var offset: Int? = null

    @Positive
    var max: Int? = null

    @Pattern(regexp = "name|href|title")
    var sort: String? = null

    @Pattern(regexp = "asc|desc|ASC|DESC")
    var order: String? = null
    // end::props[]
}
