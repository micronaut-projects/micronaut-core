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
package io.micronaut.docs.server.intro

import io.micronaut.http.MediaType

// tag::imports[]
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class[]
@Client("/hello") // <1>
interface HelloClient {

    @Get(consumes = MediaType.TEXT_PLAIN) // <2>
    Single<String> hello() // <3>
}
// end::class[]