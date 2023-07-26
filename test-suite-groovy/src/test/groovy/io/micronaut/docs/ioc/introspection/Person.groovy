package io.micronaut.docs.ioc.introspection;

// tag::class[]
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected

@Introspected
@AccessorsStyle(readPrefixes = "", writePrefixes = "") // <1>
class Person {

    private String name
    private int age

    Person(String name, int age) {
        this.name = name
        this.age = age
    }

    String name() { // <2>
        return name
    }

    void name(String name) { // <2>
        this.name = name
    }

    int age() { // <2>
        return age
    }

    void age(int age) { // <2>
        this.age = age
    }
}
// end::class[]
