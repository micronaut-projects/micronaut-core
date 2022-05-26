package io.micronaut.docs.http.server.reactive

// tag::imports[]
import io.micronaut.docs.ioc.beans.Person
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Named
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
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
        this.scheduler = Schedulers.fromExecutorService(executorService)
        this.personService = personService
    }

    @Get("/{name}")
    Mono<Person> byName(String name) {
        return Mono
                .fromCallable({ -> personService.findByName(name) }) // <2>
                .subscribeOn(scheduler) // <3>
    }
}
// end::class[]
