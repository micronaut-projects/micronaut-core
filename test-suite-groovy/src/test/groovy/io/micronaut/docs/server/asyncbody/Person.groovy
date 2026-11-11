package io.micronaut.docs.server.asyncbody

import groovy.transform.EqualsAndHashCode
import io.micronaut.core.annotation.Introspected

@Introspected
@EqualsAndHashCode
class Person {
    String name
    int age
}
