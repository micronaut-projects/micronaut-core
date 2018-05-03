package io.micronaut.security.token.jwt.bearer

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.AuthoritiesFetcher

import javax.inject.Singleton

@Requires(property = 'spec.name', value = 'accessrefershtokenloginhandler')
@Singleton
class TestingAuthoritiesFetcher implements AuthoritiesFetcher {

    @Override
    List<String> findAuthoritiesByUsername(String username) {
        (username == "admin") ?  ["ROLE_ADMIN"] : ["foo", "bar"]
    }
}
