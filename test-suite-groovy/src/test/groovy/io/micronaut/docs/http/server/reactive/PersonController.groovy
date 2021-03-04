package io.micronaut.docs.http.server.reactive

// tag::imports[]
import io.micronaut.docs.ioc.beans.Person
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

import javax.inject.Named
import java.util.concurrent.ExecutorService
// end::imports[]

// tag::class[]
@Controller("/subscribeOn/people")
class PersonController {

    private final Scheduler scheduler
    private final PersonService personService

    PersonController(
            @Named(TaskExecutors.IO) ExecutorService executorService, // <1>
            PersonService personService) {
        this.scheduler = Schedulers.from(executorService)
        this.personService = personService
    }

    @Get("/{name}")
    Single<Person> byName(String name) {
        return Single
                .fromCallable({ -> personService.findByName(name) }) // <2>
                .subscribeOn(scheduler) // <3>
    }
}
// end::class[]
