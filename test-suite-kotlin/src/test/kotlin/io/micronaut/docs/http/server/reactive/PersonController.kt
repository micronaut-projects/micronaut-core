package io.micronaut.docs.http.server.reactive

// tag::imports[]
import io.micronaut.docs.ioc.beans.Person
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import java.util.concurrent.ExecutorService
import jakarta.inject.Named
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

// end::imports[]

// tag::class[]
@Controller("/subscribeOn/people")
class PersonController internal constructor(
    @Named(TaskExecutors.IO) executorService: ExecutorService, // <1>
    private val personService: PersonService) {

    private val scheduler: Scheduler = Schedulers.fromExecutorService(executorService)

    @Get("/{name}")
    fun byName(name: String): Mono<Person> {
        return Mono
            .fromCallable { personService.findByName(name) } // <2>
            .subscribeOn(scheduler) // <3>
    }
}
// end::class[]
