package io.micronaut.docs.server.json

class Person {

    lateinit var firstName: String
    lateinit var lastName: String
    var age: Int = 0

    constructor(firstName: String, lastName: String) {
        this.firstName = firstName
        this.lastName = lastName
    }

    constructor() {}
}
