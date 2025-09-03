package io.micronaut.docs.ioc.introspection.pck.foobar

// tag::class[]

class FoobarPerson {

    private String name
    private int age

    FoobarPerson(String name, int age) {
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
