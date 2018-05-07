package io.micronaut.security.token.jwt.bearer

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.UserFetcher
import io.micronaut.security.authentication.providers.UserState
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'accessrefershtokenloginhandler')
class TestingUserFetcher implements UserFetcher {

    @Override
    Publisher<UserState> findByUsername(String username) {
        TestUserState testUserState = new TestUserState(username)
        switch (username) {
            case "disabled":
                testUserState.enabled = false
                break
            case "accountExpired":
                testUserState.accountExpired = true
                break
            case "passwordExpired":
                testUserState.passwordExpired = true
                break
            case "accountLocked":
                testUserState.accountLocked = true
                break
            case "invalidPassword":
                testUserState.password = "invalid"
                break
            case "notFound":
                testUserState = null
                break
        }
        if(testUserState != null) {
            return Flowable.just(testUserState)
        }
        else {
            return Flowable.empty()
        }
    }

    private static class TestUserState implements UserState {

        String username
        String password
        boolean enabled = true
        boolean accountExpired = false
        boolean accountLocked = false
        boolean passwordExpired = false

        TestUserState(String username) {
            this.password = "password"
            this.username = username
        }
    }
}
