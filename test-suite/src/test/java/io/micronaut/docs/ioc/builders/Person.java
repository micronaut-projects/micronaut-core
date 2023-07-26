package io.micronaut.docs.ioc.builders;

import io.micronaut.core.annotation.Introspected;

import java.util.Objects;

// tag::class[]
@Introspected(builder = @Introspected.IntrospectionBuilder(
    builderClass = Person.Builder.class
))
public class Person {
    private final String name;
    private final int age;
    private Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Person person = (Person) o;

        if (age != person.age) return false;
        return Objects.equals(name, person.name);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + age;
        return result;
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
            Objects.requireNonNull(name);
            if (age < 1) {
                throw new IllegalArgumentException("Age must be a positive number");
            }
            return new Person(name, age);
        }
    }
}
// end::class[]
