package io.micronaut.docs.http.server.reactive

// tag::imports[]
import io.micronaut.docs.ioc.beans.Person
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ExecutorService
import jakarta.inject.Named
// end::imports[]

// tag::class[]
@Controller("/subscribeOn/people")
class PersonController internal constructor(
    @Named(TaskExecutors.IO) executorService: ExecutorService, // <1>
    private val personService: PersonService) {

    private val scheduler: Scheduler = Schedulers.from(executorService)

    @Get("/{name}")
    fun byName(name: String): Single<Person> {
        return Single
            .fromCallable { personService.findByName(name) } // <2>
            .subscribeOn(scheduler) // <3>
    }
}
// end::class[]
