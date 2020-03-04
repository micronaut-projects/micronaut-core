package io.micronaut.docs.http.server.reactive;

import io.micronaut.docs.ioc.beans.Person;

import javax.inject.Singleton;

@Singleton
public class PersonService {

    public Person findByName(String name) {
        return new Person(name);
    }
}
