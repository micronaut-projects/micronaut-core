package io.micronaut.docs.http.server.reactive

import io.micronaut.docs.ioc.beans.Person

import javax.inject.Singleton

@Singleton
class PersonService {

    Person findByName(String name) {
        return new Person(name)
    }
}
