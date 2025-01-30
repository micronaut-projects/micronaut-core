package io.micronaut.docs.server.form

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*

import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "PersonControllerFormTest")
@CompileStatic
//tag::class[]
@Controller("/people")
class PersonController {

    Map<String, Person> inMemoryDatastore = new ConcurrentHashMap<>()
//end::class[]

    @Get
    Collection<Person> index() {
        inMemoryDatastore.values()
    }

//tag::formbinding[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post
    HttpResponse<Person> save(@Body Person person) {
        inMemoryDatastore.put(person.getFirstName(), person)
        HttpResponse.created(person);
    }
//end::formbinding[]
//tag::formsaveWithArgs[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgs")
    HttpResponse<Person> save(String firstName, String lastName, @Nullable Integer age) {
        Person p = new Person()
        p.firstName = firstName
        p.lastName = lastName
        if (age != null) {
            p.setAge(age)
        }
        inMemoryDatastore.put(p.firstName, p)
        return HttpResponse.created(p);
    }
//end::formsaveWithArgs[]

//tag::formsaveWithArgsOptional[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgsOptional")
    HttpResponse<Person> save(String firstName, String lastName, Optional<Integer> age) {
        Person p = new Person()
        p.firstName = firstName
        p.lastName = lastName
        age.ifPresent(p::setAge);
        inMemoryDatastore.put(p.getFirstName(), p);
        return HttpResponse.created(p);
    }
//end::formsaveWithArgsOptional[]
//tag::endclass[]
}
//end::endclass[]
