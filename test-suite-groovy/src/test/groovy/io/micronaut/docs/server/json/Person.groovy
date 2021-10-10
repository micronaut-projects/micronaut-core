package io.micronaut.docs.server.json

class Person {

    String firstName, lastName
    int age

    Person(String firstName, String lastName) {
        this.firstName = firstName
        this.lastName = lastName
    }

    Person() {
    }
}
