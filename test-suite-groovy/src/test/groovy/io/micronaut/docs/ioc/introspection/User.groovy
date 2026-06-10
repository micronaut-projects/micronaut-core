package io.micronaut.docs.ioc.introspection

// tag::imports[]
import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonSetter
import io.micronaut.core.annotation.Introspected
// end::imports[]

// tag::class[]
@Introspected
class User {
    private String displayName

    @JsonGetter('display_name')
    String displayName() {
        return displayName
    }

    @JsonSetter('display_name')
    void displayName(String displayName) {
        this.displayName = displayName
    }
}
// end::class[]
