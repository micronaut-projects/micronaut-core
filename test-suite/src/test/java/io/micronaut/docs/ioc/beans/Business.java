package io.micronaut.docs.ioc.beans;

// tag::class[]
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

import javax.annotation.concurrent.Immutable;

@Introspected
@Immutable
public class Business {

    private String name;

    private Business(String name) {
        this.name = name;
    }

    @Creator // <1>
    public static Business forName(String name) {
        return new Business(name);
    }

    public String getName() {
        return name;
    }

}
// end::class[]
