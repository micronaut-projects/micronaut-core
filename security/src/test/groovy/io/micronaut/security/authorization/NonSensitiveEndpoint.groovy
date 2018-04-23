package io.micronaut.security.authorization

import io.micronaut.management.endpoint.Endpoint
import io.micronaut.management.endpoint.Read

@Endpoint("nonSensitive")
class NonSensitiveEndpoint {

    @Read
    String hello() {
        "World"
    }
}
