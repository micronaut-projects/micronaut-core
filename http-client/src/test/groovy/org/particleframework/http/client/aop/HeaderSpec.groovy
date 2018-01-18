/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.aop

import org.particleframework.context.ApplicationContext
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Header
import org.particleframework.http.client.Client
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class HeaderSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive header"() {
        given:
        UserClient userClient = context.getBean(UserClient)
        User user = userClient.get("Fred")

        expect:
        user.username == "Fred"
        user.age == 10

    }

    @Client('/headers')
    static interface UserClient extends MyApi {

    }

    @Controller('/headers')
    static class UserController implements MyApi {

        @Override
        User get(@Header('X-Username') String username) {
            return new User(username:username, age: 10)
        }
    }

    static interface MyApi {

        @Get('/user')
        User get(@Header('X-Username') String username)
    }

    static class User {
        String username
        Integer age
    }

}
