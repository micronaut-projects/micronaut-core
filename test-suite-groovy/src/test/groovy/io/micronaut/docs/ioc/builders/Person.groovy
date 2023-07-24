package io.micronaut.docs.ioc.builders

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

//tag::class[]
import io.micronaut.core.annotation.Introspected
@CompileStatic
@Introspected(builder = @Introspected.IntrospectionBuilder(
        builderClass = Person.Builder.class
))
@EqualsAndHashCode
class Person {
    final String name
    final int age

    private Person(String name, int age) {
        this.name = name
        this.age = age
    }

    static Builder builder() {
        new Builder()
    }

    static final class Builder {
        private String name
        private int age

        Builder name(String name) {
            this.name = name
            this
        }

        Builder age(int age) {
            this.age = age
            this
        }

        Person build() {
            Objects.requireNonNull(name)
            if (age < 1) {
                throw new IllegalArgumentException("Age must be a positive number")
            }
            new Person(name, age)
        }
    }
}
//end::class[]
