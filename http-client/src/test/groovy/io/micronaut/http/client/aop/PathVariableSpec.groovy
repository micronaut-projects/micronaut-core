package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PathVariableSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive with path variable"() {
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

    @Client('/path-variables')
    static interface UserClient extends MyApi {

    }

    @Controller('/path-variables')
    static class UserController implements MyApi {

        @Override
        User get(String username) {
            return new User(username:username, age: 10)
        }

        @Override
        User findByAge(Integer age) {
            return new User(username:"John", age: 18)
        }
    }

    static interface MyApi {

        @Get('/user/{X-username}')
        User get(@PathVariable('X-username') String username)

        @Get('/user/age/{userAge}')
        User findByAge(@PathVariable('userAge') Integer age)
    }

    static class User {
        String username
        Integer age
    }
}
