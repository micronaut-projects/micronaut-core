package io.micronaut.docs.ioc.introspection;

// tag::class[]
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;

@Introspected
@AccessorsStyle(readPrefixes = "", writePrefixes = "") // <1>
public class Person {

    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String name() { // <2>
        return name;
    }

    public void name(String name) { // <2>
        this.name = name;
    }

    public int age() { // <2>
        return age;
    }

    public void age(int age) { // <2>
        this.age = age;
    }
}
// end::class[]
