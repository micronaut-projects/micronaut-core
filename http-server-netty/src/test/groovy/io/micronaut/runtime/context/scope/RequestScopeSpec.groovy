/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.runtime.context.scope


import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.AbstractMicronautSpec
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Marcel Overdijk
 * @since 1.2
 */
class RequestScopeSpec extends AbstractMicronautSpec {

    void "test @Request bean created per request"() {

        given:
        def requestCustomScope = applicationContext.getBean(RequestCustomScope)
        def conditions = new PollingConditions(timeout: 10)

        when:
        def result = rxClient.retrieve(HttpRequest.GET("/test-request-scope"), String).blockingFirst()

        then:
        result == "message count 1, count within request 1"

        when:
        result = rxClient.retrieve(HttpRequest.GET("/test-request-scope"), String).blockingFirst()

        then:
        result == "message count 2, count within request 1"

        when:
        result = rxClient.retrieve(HttpRequest.GET("/test-request-scope"), String).blockingFirst()

        then:
        result == "message count 3, count within request 1"

        when:
        System.gc()

        then:
        conditions.eventually {
            requestCustomScope.@requestScope.size() == 0
        }

        cleanup:
        embeddedServer.stop()
    }

    @Request
    static class RequestBean {

        int num = 0

        int count() {
            num++
            return num
        }
    }

    @Singleton
    static class MessageService {

        @Inject
        RequestBean requestBean

        int num = 0

        int count() {
            num++
            return num
        }

        String getMessage() {
            return "message count ${count()}, count within request ${requestBean.count()}"
        }
    }

    @Controller
    static class TestController {

        @Inject
        MessageService messageService

        @Get("/test-request-scope")
        String test() {
            return messageService.message
        }
    }
}
