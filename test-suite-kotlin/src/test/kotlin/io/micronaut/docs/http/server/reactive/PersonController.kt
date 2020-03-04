package io.micronaut.docs.http.server.reactive

import io.micronaut.docs.ioc.beans.Person

// tag::imports[]
import io.micronaut.http.annotation.*
import io.micronaut.scheduling.TaskExecutors
import io.reactivex.*
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ExecutorService
import javax.inject.Named
// end::imports[]

// tag::class[]
@Controller("/subscribeOn/people")
class PersonController internal constructor(
        @Named(TaskExecutors.IO) executorService: ExecutorService, // <1>
        val personService: PersonService) {
    private val scheduler: Scheduler

    init {
        scheduler = Schedulers.from(executorService)
    }

    @Get("/{name}")
    fun byName(name: String): Single<Person> {
        return Single.fromCallable { personService.findByName(name) } // <2>
                .subscribeOn(scheduler) // <3>
    }
}
// end::class[]