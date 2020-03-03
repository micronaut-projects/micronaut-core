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
package io.micronaut.docs.sse

import io.micronaut.docs.streaming.Headline
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import reactor.core.publisher.Flux

// tag::class[]
@Client("/streaming/sse")
interface HeadlineClient {

    @Get(value = "/headlines", processes = MediaType.TEXT_EVENT_STREAM)
    Flux<Event<Headline>> streamHeadlines()
}
// end::class[]