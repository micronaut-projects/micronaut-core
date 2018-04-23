package io.micronaut.security.authorization

import io.micronaut.management.endpoint.Endpoint
import io.micronaut.management.endpoint.Read

@Endpoint(id = "sensitive", defaultSensitive = true)
class SensitiveEndpoint {

    @Read
    String hello() {
        "World"
    }
}
