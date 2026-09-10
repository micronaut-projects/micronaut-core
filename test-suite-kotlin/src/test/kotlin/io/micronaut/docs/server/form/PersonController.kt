package io.micronaut.docs.server.form

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "PersonControllerFormTest")
//tag::class[]
@Controller("/people")
class PersonController {

    val inMemoryDatastore: MutableMap<String, Person> = ConcurrentHashMap()
//end::class[]

//tag::formbinding[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post
    fun save(@Body person: Person): HttpResponse<Person> {
        inMemoryDatastore[person.firstName!!] = person
        return HttpResponse.created(person)
    }
//end::formbinding[]

//tag::formsaveWithArgs[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgs")
    fun save(firstName: String, lastName: String, age: Int?): HttpResponse<Person> {
        val p = Person(firstName, lastName)
        if (age != null) {
            p.age = age
        }
        inMemoryDatastore[p.firstName!!] = p
        return HttpResponse.created(p)
    }
//end::formsaveWithArgs[]

//tag::formsaveWithArgsOptional[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgsOptional")
    fun save(firstName: String, lastName: String, age: Optional<Int>): HttpResponse<Person> {
        val p = Person(firstName, lastName)
        age.ifPresent { p.age = it }
        inMemoryDatastore[p.firstName!!] = p
        return HttpResponse.created(p)
    }
//end::formsaveWithArgsOptional[]

//tag::endclass[]
}
//end::endclass[]
