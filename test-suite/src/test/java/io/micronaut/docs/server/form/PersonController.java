package io.micronaut.docs.server.form;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Requires(property = "spec.name", value = "PersonControllerFormTest")
//tag::class[]
@Controller("/people")
class PersonController {

    Map<String, Person> inMemoryDatastore = new ConcurrentHashMap<>();
//end::class[]


//tag::formbinding[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post
    HttpResponse<Person> save(@Body Person person) {
        inMemoryDatastore.put(person.getFirstName(), person);
        return HttpResponse.created(person);
    }
//end::formbinding[]

//tag::formsaveWithArgs[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgs")
    HttpResponse<Person> save(String firstName, String lastName, @Nullable Integer age) {
        Person p = new Person(firstName, lastName);
        if (age != null) {
            p.setAge(age);
        }
        inMemoryDatastore.put(p.getFirstName(), p);
        return HttpResponse.created(p);
    }
//end::formsaveWithArgs[]

//tag::formsaveWithArgsOptional[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgsOptional")
    HttpResponse<Person> save(String firstName, String lastName, Optional<Integer> age) {
        Person p = new Person(firstName, lastName);
        age.ifPresent(p::setAge);
        inMemoryDatastore.put(p.getFirstName(), p);
        return HttpResponse.created(p);
    }
//end::formsaveWithArgsOptional[]

//tag::endclass[]
}
//end::endclass[]
