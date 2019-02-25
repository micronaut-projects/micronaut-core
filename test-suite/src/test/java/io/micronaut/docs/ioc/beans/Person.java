package io.micronaut.docs.ioc.beans;

// tag::imports[]
import io.micronaut.core.annotation.Introspected;
// end::imports[]

// tag::class[]
@Introspected
public class Person {

    private String name;

    public Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
// end::class[]

