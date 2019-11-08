package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

import javax.annotation.concurrent.Immutable

@Introspected
@Immutable
class Business {

    private String name

    private Business(String name) {
        this.name = name
    }

    @Creator // <1>
    static Business forName(String name) {
        new Business(name)
    }

    String getName() {
        name
    }

}
// end::class[]
