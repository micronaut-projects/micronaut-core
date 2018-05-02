package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.UserFetcher
import io.micronaut.security.authentication.providers.UserState

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'authorization')
class TestingUserFetcher implements UserFetcher {

    @Override
    Optional<UserState> findByUsername(String username) {
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
        return Optional.ofNullable(testUserState)
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
