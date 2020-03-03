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
package io.micronaut.docs.streaming

// tag::imports[]

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZonedDateTime

// end::imports[]

@Controller("/streaming")
class HeadlineFlowController {

    // tag::streamingWithFlow[]
    @Get(value = "/headlinesWithFlow", processes = [MediaType.APPLICATION_JSON_STREAM])
    internal fun streamHeadlinesWithFlow(): Flow<Headline> = // <1>
        flow { // <2>
            repeat(100) { // <3>
                with (Headline()) {
                    text = "Latest Headline at " + ZonedDateTime.now()
                    emit(this) // <4>
                    delay(1_000) // <5>
                }
            }
        }
    // end::streamingWithFlow[]
}
