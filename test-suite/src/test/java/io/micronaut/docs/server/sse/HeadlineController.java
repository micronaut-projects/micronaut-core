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
package io.micronaut.docs.server.sse;

// tag::imports[]
import io.micronaut.http.annotation.*;
import io.micronaut.http.sse.Event;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
// end::imports[]

// tag::class[]
@Controller("/headlines")
public class HeadlineController {

    @Get
    public Publisher<Event<Headline>> index() { // <1>
        String[] versions = new String[]{"1.0", "2.0"}; // <2>

        return Flowable.generate(() -> 0, (i, emitter) -> { // <3>
            if (i < versions.length) {
                emitter.onNext( // <4>
                    Event.of(new Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
                );
            } else {
                emitter.onComplete(); // <5>
            }
            return ++i;
        });
    }
}
// end::class[]
