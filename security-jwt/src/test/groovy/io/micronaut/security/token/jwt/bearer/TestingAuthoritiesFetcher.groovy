package io.micronaut.security.token.jwt.bearer

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.AuthoritiesFetcher
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Singleton

@Requires(property = 'spec.name', value = 'accessrefershtokenloginhandler')
@Singleton
class TestingAuthoritiesFetcher implements AuthoritiesFetcher {

    @Override
    Publisher<List<String>> findAuthoritiesByUsername(String username) {
        Flowable.just((username == "admin") ?  ["ROLE_ADMIN"] : ["foo", "bar"])
    }
}
