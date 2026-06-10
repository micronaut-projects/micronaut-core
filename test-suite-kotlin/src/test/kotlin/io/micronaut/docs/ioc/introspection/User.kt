package io.micronaut.docs.ioc.introspection

// tag::imports[]
import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonSetter
import io.micronaut.core.annotation.Introspected
// end::imports[]

// tag::class[]
@Introspected
class User {
    private var displayName: String? = null

    @JsonGetter("display_name")
    fun displayName(): String? {
        return displayName
    }

    @JsonSetter("display_name")
    fun displayName(displayName: String?) {
        this.displayName = displayName
    }
}
// end::class[]
