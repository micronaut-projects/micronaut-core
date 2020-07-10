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
package io.micronaut.docs.http.server.reactive;

import io.micronaut.docs.ioc.beans.Person;

// tag::imports[]
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.*;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Named;
import java.util.concurrent.ExecutorService;
// end::imports[]

// tag::class[]
@Controller("/subscribeOn/people")
public class PersonController {

    private final Scheduler scheduler;
    private final PersonService personService;

    PersonController(
            @Named(TaskExecutors.IO) ExecutorService executorService, // <1>
            PersonService personService) {
        this.scheduler = Schedulers.from(executorService);
        this.personService = personService;
    }

    @Get("/{name}")
    Single<Person> byName(String name) {
        return Single.fromCallable(() ->
                personService.findByName(name) // <2>
        ).subscribeOn(scheduler); // <3>
    }
}
// end::class[]
