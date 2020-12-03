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
package io.micronaut.runtime.http.scope

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import io.micronaut.http.server.netty.AbstractMicronautSpec
import spock.util.concurrent.PollingConditions

import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Marcel Overdijk
 * @since 1.2.0
 */
class RequestScopeSpec extends AbstractMicronautSpec {

    void "test @Request bean created per request"() {
        given:
        PollingConditions conditions = new PollingConditions(delay: 0.5, timeout: 3)
        ReqTerminatedListener listener = applicationContext.getBean(ReqTerminatedListener)

        when:
        def result = rxClient.retrieve(HttpRequest.GET("/test-request-scope"), String).blockingFirst()

        then:
        result == "message count 1, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            listener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }

        when:
        RequestBean.BEANS_CREATED.clear()
        RequestScopeFactoryBean.BEANS_CREATED.clear()
        listener.callCount = 0
        result = rxClient.retrieve(HttpRequest.GET("/test-request-scope"), String).blockingFirst()

        then:
        result == "message count 2, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            listener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }

        when:
        RequestBean.BEANS_CREATED.clear()
        RequestScopeFactoryBean.BEANS_CREATED.clear()
        listener.callCount = 0
        result = rxClient.retrieve(HttpRequest.GET("/test-request-scope"), String).blockingFirst()

        then:
        result == "message count 3, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            listener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }
    }

    void "test request scope bean that injects the request"() {
        when:
        String result = rxClient.retrieve(HttpRequest.GET("/test-request-aware"), String).blockingFirst()

        then:
        result == "OK"
    }

    @RequestScope
    static class RequestBean {

        static final Set<RequestBean> BEANS_CREATED = new HashSet<>()

        RequestBean() {
            // don't add the proxy
            if (getClass() == RequestBean) {
                BEANS_CREATED.add(this)
            }
        }

        int num = 0
        boolean dead = false
        int count() {
            num++
            return num
        }

        @PreDestroy
        void killMe() {
            this.dead = true
        }
    }

    @RequestScope
    static class RequestAwareBean implements RequestAware {

        HttpRequest<?> request

        @Override
        void setRequest(HttpRequest<?> request) {
            this.request = request
        }
    }

    @Singleton
    static class MessageService {

        @Inject
        RequestBean requestBean

        @Inject
        RequestScopeFactoryBean requestScopeFactoryBean

        int num = 0

        int count() {
            num++
            return num
        }

        String getMessage() {
            int count1 = requestBean.count()
            int count2 = requestScopeFactoryBean.count()
            assert count1 == count2
            return "message count ${count()}, count within request ${count1}"
        }
    }

    @Controller
    static class TestController {

        @Inject
        MessageService messageService

        @Inject
        RequestAwareBean requestAwareBean

        @Get("/test-request-scope")
        String test() {
            return messageService.message
        }

        @Get("/test-request-aware")
        String testAware(HttpRequest request) {
            if (requestAwareBean.request == request) {
                return "OK"
            }
            throw new IllegalStateException("Request does not match")
        }
    }

    @Singleton
    static class ReqTerminatedListener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        int callCount

        @Override
        void onApplicationEvent(HttpRequestTerminatedEvent event) {
            callCount++
        }
    }
}
