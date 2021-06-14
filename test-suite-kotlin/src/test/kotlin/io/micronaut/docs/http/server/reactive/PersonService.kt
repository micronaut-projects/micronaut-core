package io.micronaut.docs.http.server.reactive

import io.micronaut.docs.ioc.beans.Person
import jakarta.inject.Singleton

@Singleton
class PersonService {
    fun findByName(name: String): Person {
        return Person(name)
    }
}
