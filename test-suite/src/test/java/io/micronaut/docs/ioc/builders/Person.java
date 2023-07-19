package io.micronaut.docs.ioc.builders;

import io.micronaut.core.annotation.Introspected;

// tag::class[]
@Introspected(builder = @Introspected.IntrospectionBuilder(
    builderClass = Person.Builder.class
))
public record Person(String name, int age) {
    public Person {
        if (name == null) {
            throw new IllegalArgumentException("Name must be specified");
        }
        if (age < 1) {
            throw new IllegalArgumentException("Age must be a positive number");
        }
    }
    public static final class Builder {
        private String name;
        private int age;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Person build() {
            return new Person(name, age);
        }
    }
}
// end::class[]
