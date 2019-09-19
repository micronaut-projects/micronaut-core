package io.micronaut.docs.server.json

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class Person {

    var firstName: String? = null
    var lastName: String? = null
    var age: Int = 0

    constructor(firstName: String, lastName: String) {
        this.firstName = firstName
        this.lastName = lastName
    }

    constructor() {}
}
