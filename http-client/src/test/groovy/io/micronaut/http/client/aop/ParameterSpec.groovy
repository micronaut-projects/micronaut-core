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
package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author puneetbehl
 * @since 1.0
 */
class ParameterSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive parameter"() {
        given:
        UserClient userClient = context.getBean(UserClient)
        User user = userClient.get("Fred")

        expect:
        user.username == "Fred"
        user.age == 10

        when:
        user = userClient.findByAge(18)

        then:
        user.username == "John"
        user.age == 18

    }

    @Client('/parameters')
    static interface UserClient extends MyApi {

    }

    @Controller('/parameters')
    static class UserController implements MyApi {

        @Override
        User get(@QueryValue('X-username') String username) {
            return new User(username:username, age: 10)
        }

        @Override
        User findByAge(@QueryValue('userAge') Integer age) {
            return new User(username:"John", age: 18)
        }
    }

    static interface MyApi {

        @Get('/user/{X-username}')
        User get(@QueryValue('X-username') String username)

        @Get('/user/age/{userAge}')
        User findByAge(@QueryValue('userAge') Integer age)
    }

    static class User {
        String username
        Integer age
    }

}
