package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

import javax.annotation.concurrent.Immutable

@Introspected
@Immutable
class Business private constructor(val name: String) {
    companion object {

        @Creator // <1>
        fun forName(name: String): Business {
            return Business(name)
        }
    }

}
// end::class[]
