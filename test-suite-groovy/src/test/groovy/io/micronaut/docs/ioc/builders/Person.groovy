package io.micronaut.docs.ioc.builders

import groovy.transform.Canonical
import io.micronaut.core.annotation.Introspected


@Canonical
@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = Builder))
class Person {
    String name
    int age

    static final class Builder {
        private String name
        private int age

        Builder name(String name) {
            this.name = name
            return this
        }

        Builder age(int age) {
            this.age = age
            return this
        }

        Person build() {
            return new Person(name, age)
        }
    }
}
