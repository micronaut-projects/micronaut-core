/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client.docs.annotation;

// tag::imports[]
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import io.reactivex.Single;

import javax.validation.constraints.*;
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class[]
@Validated
public interface PetOperations {
    // tag::save[]
    @Post
    Single<Pet> save(@NotBlank String name, @Min(1L) int age);
    // end::save[]
}
// end::class[]
