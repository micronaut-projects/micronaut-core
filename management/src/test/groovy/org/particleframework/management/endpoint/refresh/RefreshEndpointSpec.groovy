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
package org.particleframework.management.endpoint.refresh

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.ConfigurationProperties
import org.particleframework.context.annotation.Value
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.runtime.context.scope.Refreshable
import org.particleframework.runtime.server.EmbeddedServer
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

        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/refreshTest")).build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'test test'

        when:
        System.setProperty("foo.bar", "changed")
        RequestBody reqbody = RequestBody.create(null, new byte[0])
        def request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/refresh"))
                .post(reqbody)

        response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string().contains('"foo.bar"')

        when:
        response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/refreshTest")).build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'changed changed'

        cleanup:
        embeddedServer.close()
    }


    @Controller("/refreshTest")
    static class TestController {
        private final RefreshBean refreshBean;

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

        @Value('foo.bar')
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
