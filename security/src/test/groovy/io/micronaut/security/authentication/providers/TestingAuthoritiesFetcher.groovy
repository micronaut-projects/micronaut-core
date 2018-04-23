package io.micronaut.security.authentication.providers

import io.micronaut.context.annotation.Requires
import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.authentication')
class TestingAuthoritiesFetcher implements AuthoritiesFetcher {

    @Override
    List<String> findAuthoritiesByUsername(String username) {
        (username == "admin") ?  ["ROLE_ADMIN"] : ["foo", "bar"]
    }
}
