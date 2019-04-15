package io.micronaut.docs.ioc.validation;

// tag::imports[]
import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;
// end::imports[]

// tag::class[]
@Singleton
public class PersonService {
    public void sayHello(@NotBlank String name) {
        System.out.println("Hello " + name);
    }
}
// end::class[]
