/*
 * Copyright 2017 original authors
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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RefreshEndpointSpec extends Specification {


    void "test refresh endpoint"() {
        given:
        System.setProperty("foo.bar", "test")
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
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
        response = rxClient.exchange("/refreshTest", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'changed changed'

        cleanup:
        embeddedServer.close()
    }


    @Controller("/refreshTest")
    static class TestController {
        private final RefreshBean refreshBean

        TestController(RefreshBean refreshBean) {
            this.refreshBean = refreshBean
        }

        @Get('/')
        String index() {
            refreshBean.testConfigProps() + ' ' + refreshBean.testValue()
        }
    }

    @Refreshable
    static class RefreshBean {

        final MyConfig config

        @Value('${foo.bar}')
        String foo

        RefreshBean(MyConfig config) {
            this.config = config
        }

        String testValue() {
            return foo
        }

        String testConfigProps() {
            return config.bar
        }
    }

    @ConfigurationProperties('foo')
    static class MyConfig {
        String bar
    }

}
