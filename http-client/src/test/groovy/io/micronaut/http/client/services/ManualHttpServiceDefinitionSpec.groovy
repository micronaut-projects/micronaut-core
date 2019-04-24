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
package io.micronaut.http.client.services

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.RxHttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.time.Duration

class ManualHttpServiceDefinitionSpec extends Specification {


    void "test that manually defining an HTTP client creates a client bean"() {
        given:
        EmbeddedServer firstApp = ApplicationContext.run(EmbeddedServer)


        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.url': firstApp.getURI(),
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms',
                'micronaut.http.services.foo.read-timeout':'15s',
                'micronaut.http.services.foo.pool.enabled':false,
                'micronaut.http.services.bar.url': firstApp.getURI(),
                'micronaut.http.services.bar.path': '/manual/http/service',
                'micronaut.http.services.bar.health-check':true,
                'micronaut.http.services.bar.health-check-interval':'100ms',
                'micronaut.http.services.bar.read-timeout':'10s',
                'micronaut.http.services.bar.pool.enabled':true
        )
        TestClientFoo tcFoo = clientApp.getBean(TestClientFoo)
        TestClientBar tcBar = clientApp.getBean(TestClientBar)

        when:'the config is retrieved'
        def config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("foo"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(15)
        !config.getConnectionPoolConfiguration().isEnabled()

        when:
        RxHttpClient client = clientApp.getBean(RxHttpClient, Qualifiers.byName("foo"))
        String result = client.retrieve('/').blockingFirst()

        then:
        client.configuration == config
        result == 'ok'
        tcFoo.index() == 'ok'

        when:'the config is retrieved'
        config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("bar"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(10)
        config.getConnectionPoolConfiguration().isEnabled()

        when:
        client = clientApp.getBean(RxHttpClient, Qualifiers.byName("bar"))
        result = client.retrieve(HttpRequest.POST('/', '')).blockingFirst()
        then:
        client.configuration == config
        result == 'created'
        tcBar.save() == 'created'
        tcBar.update() == "updated"

        cleanup:
        firstApp.close()
        clientApp.close()
    }


    void "test that manually defining an HTTP client without URL doesn't create bean"() {
        given:
        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms',
                'micronaut.http.services.foo.read-timeout':'15s',
                'micronaut.http.services.foo.pool.enabled':false
        )

        when:'the config is retrieved'
        def config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("foo"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(15)
        !config.getConnectionPoolConfiguration().isEnabled()

        when:
        def opt = clientApp.findBean(RxHttpClient, Qualifiers.byName("foo"))

        then:
        !opt.isPresent()

        cleanup:
        clientApp.close()
    }

    @Client(id = "foo")
    static interface TestClientFoo {
        @Get
        String index()
    }

    @Client("bar")
    static interface TestClientBar {
        @Post
        String save()

        @Put("update")
        String update()
    }

    @Controller('manual/http/service')
    static class TestController {
        @Get
        String index() {
            return "ok"
        }

        @Post
        String save() {
            return "created"
        }

        @Put("update")
        String update() {
            return "updated"
        }
    }
}
