/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.UserFetcher
import io.micronaut.security.authentication.providers.UserState
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'authorization')
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
