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
import io.reactivex.Flowable

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
// end::imports[]

@Controller("/streaming")
class HeadlineController {

    // tag::streaming[]
    @Get(value = "/headlines", processes = MediaType.APPLICATION_JSON_STREAM) // <1>
    Flowable<Headline> streamHeadlines() {
        Flowable.fromCallable({ // <2>
            Headline headline = new Headline()
            headline.setText("Latest Headline at " + ZonedDateTime.now())
            return headline
        }).repeat(100) // <3>
          .delay(1, TimeUnit.SECONDS) // <4>
    }
    // end::streaming[]
}
