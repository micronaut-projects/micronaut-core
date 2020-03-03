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
package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.annotation.RequestAttributes
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

// tag::class[]
@Client("/story")
@RequestAttributes(RequestAttribute(name = "client-name", value = "storyClient"), RequestAttribute(name = "version", value = "1"))
interface StoryClient {

    @Get("/{storyId}")
    fun getById(@RequestAttribute storyId: String): Single<Story>
}
// end::class[]
