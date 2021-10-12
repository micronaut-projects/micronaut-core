package io.micronaut.docs.ioc.beans;

// tag::class[]
import io.micronaut.core.annotation.Introspected;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class User {
    public final String name; // <1>
    public int age = 18; // <2>

    public User(String name) {
        this.name = name;
    }
}
// end::class[]
