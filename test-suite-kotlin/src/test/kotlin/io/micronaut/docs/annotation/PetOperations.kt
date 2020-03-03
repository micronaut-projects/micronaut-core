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
package io.micronaut.docs.annotation

// tag::imports[]

import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.reactivex.Single

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

// end::imports[]

// tag::class[]
@Validated
interface PetOperations {
    // tag::save[]
    @Post
    fun save(@NotBlank name: String, @Min(1L) age: Int): Single<Pet>
    // end::save[]
}
// end::class[]
