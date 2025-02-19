package io.micronaut.docs.server.form

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode;

import io.micronaut.core.annotation.Introspected

@Introspected
@CompileStatic
@EqualsAndHashCode
class Person {
    String firstName
    String lastName
    int age
}
