package io.micronaut.docs.http.server.scheduleon;

import io.micronaut.docs.http.server.reactive.PersonService;
import io.micronaut.docs.ioc.beans.Person;

// tag::imports[]
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ScheduleOn;
// end::imports[]

// tag::class[]
@Controller("/scheduleOn/people")
public class PersonController {

    private final PersonService personService;

    PersonController(
            PersonService personService) {
        this.personService = personService;
    }

    @Get("/{name}")
    @ScheduleOn(TaskExecutors.IO) // <1>
    Person byName(String name) {
        return personService.findByName(name);
    }
}
// end::class[]

