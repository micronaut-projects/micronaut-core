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
@Controller("/people")
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
