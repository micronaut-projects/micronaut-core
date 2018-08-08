package io.micronaut.docs

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@Canonical
@CompileStatic
class Person {
    String username
    boolean loggedIn
}
