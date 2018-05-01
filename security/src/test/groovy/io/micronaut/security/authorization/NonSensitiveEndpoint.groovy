package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.management.endpoint.Endpoint
import io.micronaut.management.endpoint.Read

import javax.annotation.Nullable
import java.security.Principal

@Requires(property = 'spec.name', value = 'authorization')
@Endpoint("nonSensitive")
class NonSensitiveEndpoint {

    @Read
    String hello(@Nullable Principal principal) {
        if (principal == null) {
            "Not logged in"
        } else {
            "Logged in as ${principal.name}"
        }
    }
}
