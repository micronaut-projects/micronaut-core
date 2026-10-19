/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.streaming

// tag::imports[]
import io.micronaut.http.MediaType.APPLICATION_JSON_STREAM
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.BodyElements
import io.micronaut.http.client.annotation.Client
import java.util.concurrent.CompletionStage
// end::imports[]

// tag::class[]
@Client("/streaming")
interface AsyncHeadlineClient {

    @Get(value = "/headlines", processes = [APPLICATION_JSON_STREAM])
    fun streamHeadlines(): CompletionStage<BodyElements<Headline>> // <1>
}
// end::class[]
