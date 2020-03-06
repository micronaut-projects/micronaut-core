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
package io.micronaut.docs.server.sse

// tag::imports[]

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.sse.Event
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ScheduleOn
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import org.reactivestreams.Publisher
import java.util.concurrent.Callable

// end::imports[]

// tag::class[]
@Controller("/headlines")
class HeadlineController {

    @ScheduleOn(TaskExecutors.IO)
    @Get(produces = [MediaType.TEXT_EVENT_STREAM])
    fun index(): Publisher<Event<Headline>> { // <1>
        val versions = arrayOf("1.0", "2.0") // <2>

        return Flowable.generate<Event<Headline>, Int>(Callable<Int>{ 0 }, BiFunction { // <3>
            i: Int, emitter: Emitter<Event<Headline>> ->
            var nextInt: Int = i
            if (i < versions.size) {
                emitter.onNext( // <4>
                        Event.of<Headline>(Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
                )
            } else {
                emitter.onComplete() // <5>
            }
            ++nextInt
        })
    }
}
// end::class[]