package io.micronaut.docs.ioc.introspection.pck.foobar;

// tag::class[]

public class AbcPerson {

    private String name;
    private int age;

    public AbcPerson(String name, int age) {
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
