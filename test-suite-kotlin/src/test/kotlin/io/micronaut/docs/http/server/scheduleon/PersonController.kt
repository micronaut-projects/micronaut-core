package io.micronaut.docs.http.server.scheduleon

import io.micronaut.docs.http.server.reactive.PersonService
import io.micronaut.docs.ioc.beans.Person
// tag::imports[]
import io.micronaut.http.annotation.*
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ScheduleOn
// end::imports[]
// tag::class[]
@Controller("/people")
class PersonController (private val personService: PersonService) {
    @Get("/{name}")
    @ScheduleOn(TaskExecutors.IO) // <1>
    fun byName(name: String): Person {
        return personService.findByName(name)
    }
}
// end::class[]