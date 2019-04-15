package io.micronaut.docs.ioc.validation.pojo;


// tag::imports[]
import io.micronaut.docs.ioc.validation.Person;

import javax.inject.Singleton;
import javax.validation.Valid;
// end::imports[]

// tag::class[]
@Singleton
public class PersonService {
    public void sayHello(@Valid Person person) {
        System.out.println("Hello " + person.getName());
    }
}
// end::class[]

