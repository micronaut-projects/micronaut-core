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
package io.micronaut.management.endpoint.refresh

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import spock.util.environment.RestoreSystemProperties

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@RestoreSystemProperties
class RefreshEndpointSpec extends Specification {

    void "test refresh endpoint"() {
        given:
        System.setProperty("foo.bar", "test")
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.refresh.sensitive': false], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange("/refreshTest", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'test test'

        when:
        System.setProperty("foo.bar", "changed")
        response = rxClient.exchange(HttpRequest.POST("/refresh", new byte[0]), String).blockingFirst()


        then:
        response.code() == HttpStatus.OK.code
        response.body().contains('"foo.bar"')

        when:
        PollingConditions conditions = new PollingConditions(timeout: 3)


        then:
        conditions.eventually {
            def res = rxClient.exchange("/refreshTest", String).blockingFirst()
            res.code() == HttpStatus.OK.code
            res.body() == 'changed changed'

        }
1
        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void "test refresh endpoint with all parameter"() {
        given:
        System.setProperty("foo.bar", "test")
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.refresh.sensitive': false], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange("/refreshTest/external", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        String firstResponse = response.body()

        when: "/refresh is called"
        response = rxClient.exchange(HttpRequest.POST("/refresh", new byte[0]), String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code

        when:
        response = rxClient.exchange("/refreshTest/external", String).blockingFirst()

        then: "subsequent response does not change"
        response.code() == HttpStatus.OK.code
        response.body() == firstResponse

        when: "/refresh is called with `all` body parameter"
        response = rxClient.exchange(HttpRequest.POST("/refresh", '{"force": true}'), String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code

        when:
        PollingConditions conditions = new PollingConditions(timeout: 3)

        then: "Response is now different"
        conditions.eventually {
            def res = rxClient.exchange("/refreshTest/external", String).blockingFirst()
            res.code() == HttpStatus.OK.code
            res.body() != firstResponse
        }


        cleanup:
        rxClient.close()
        embeddedServer.close()
    }
    
    @Controller("/refreshTest")
    static class TestController {
        private final RefreshBean refreshBean

        TestController(RefreshBean refreshBean) {
            this.refreshBean = refreshBean
        }

        @Get
        String index() {
            refreshBean.testConfigProps() + ' ' + refreshBean.testValue()
        }

        @Get('/external')
        String external() {
            "${refreshBean.testExternal()} ${refreshBean.external}"
        }
    }

    @Refreshable
    static class RefreshBean {

        final MyConfig config

        @Value('${foo.bar}')
        String foo
        Integer external

        RefreshBean(MyConfig config) {
            this.config = config
        }

        String testValue() {
            return foo
        }

        Integer testExternal() {
            if (!external) external = testExternalResource()

            external
        }

        String testConfigProps() {
            return config.bar
        }

        static Integer testExternalResource() {
            return new Date().time.intValue()
        }
    }

    @ConfigurationProperties('foo')
    static class MyConfig {
        String bar
    }

}
