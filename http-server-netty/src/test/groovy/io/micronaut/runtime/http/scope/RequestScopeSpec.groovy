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

import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.event.HttpRequestReceivedEvent
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
/**
 * @author Marcel Overdijk
 * @since 1.2.0
 */
class RequestScopeSpec extends AbstractMicronautSpec {

    @Shared
    PollingConditions conditions = new PollingConditions(delay: 0.5, timeout: 3)

    def setup() {
        ReqTerminatedListener terminatedListener = applicationContext.getBean(ReqTerminatedListener)
        ReqReceivedListener receivedListener = applicationContext.getBean(ReqReceivedListener)
        terminatedListener.callCount = 0
        receivedListener.callCount = 0
        SimpleBean.destroyed.set(0)
    }

    void "test dependent beans leak"() {
        when:
        (0..100).each {
            httpClient.toBlocking().retrieve(HttpRequest.GET("/test-simple-request-scope"), String)
        }
        def controller = applicationContext.getBean(SimpleTestController)
        then:
        conditions.eventually {
            SimpleBean.destroyed.get() == 101
        }
        (controller.simpleRequestBean.$beanResolutionContext.popDependentBeans() as Collection) == null
    }

    void 'test request scope no request'() {
        when:
        RequestBean requestBean = applicationContext.getBean(RequestBean)
        requestBean.count()

        then:
        def e = thrown(RuntimeException)
        e.message == 'No request present'

        cleanup:
        RequestBean.BEANS_CREATED.clear()
    }

    void "test @Request bean created per request"() {
        given:
        ReqTerminatedListener terminatedListener = applicationContext.getBean(ReqTerminatedListener)
        ReqReceivedListener receivedListener = applicationContext.getBean(ReqReceivedListener)


        when:
        def result = httpClient.toBlocking().retrieve(HttpRequest.GET("/test-request-scope"), String)

        then:
        result == "message count 1, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            terminatedListener.callCount == 1
            receivedListener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }

        when:
        RequestBean.BEANS_CREATED.clear()
        RequestScopeFactoryBean.BEANS_CREATED.clear()
        terminatedListener.callCount = 0
        result = httpClient.toBlocking().retrieve(HttpRequest.GET("/test-request-scope"), String)

        then:
        result == "message count 2, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            terminatedListener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }

        when:
        RequestBean.BEANS_CREATED.clear()
        RequestScopeFactoryBean.BEANS_CREATED.clear()
        terminatedListener.callCount = 0
        result = httpClient.toBlocking().retrieve(HttpRequest.GET("/test-request-scope"), String)

        then:
        result == "message count 3, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            terminatedListener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }

        when:
        RequestBean.BEANS_CREATED.clear()
        RequestScopeFactoryBean.BEANS_CREATED.clear()
        terminatedListener.callCount = 0
        result = httpClient.toBlocking().retrieve(HttpRequest.GET("/test-request-scope-stream"), String)

        then:
        result == "message count 4, count within request 1"
        RequestBean.BEANS_CREATED.size() == 1
        RequestScopeFactoryBean.BEANS_CREATED.size() == 1
        conditions.eventually {
            terminatedListener.callCount == 1
            RequestBean.BEANS_CREATED.first().dead
            RequestScopeFactoryBean.BEANS_CREATED.first().dead
        }
    }

    void "test request scope bean that injects the request"() {
        when:
        String result = httpClient.toBlocking().retrieve(HttpRequest.GET("/test-request-aware"), String)

        then:
        result == "OK"
    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
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

    @Requires(property = "spec.name", value = "RequestScopeSpec")
    @RequestScope
    static class SimpleRequestBean {

        private final SimpleBean simpleBean
        final HttpClient client

        SimpleRequestBean(
                SimpleBean simpleBean,
                @Client("/") HttpClient client) {
            this.simpleBean = simpleBean
            this.client = client
        }

        String sayHello() {
            return client.toBlocking().retrieve("/test-simple-request-scope-other")
        }

    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
    @Prototype
    static class SimpleBean {

        static AtomicInteger destroyed = new AtomicInteger()

        @PreDestroy
        void destroy() {
            destroyed.incrementAndGet()
        }

    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
    @Controller
    static class SimpleTestController {
        final SimpleRequestBean simpleRequestBean
        final HttpClient client

        SimpleTestController(
                SimpleRequestBean simpleRequestBean,
                @Client("/") HttpClient client) {
            this.simpleRequestBean = simpleRequestBean
            this.client = client
        }

        @Get("/test-simple-request-scope")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String test() {
            return simpleRequestBean.sayHello()
        }

        @Get("/test-simple-request-scope-other")
        String test2() {
            simpleRequestBean.client.is(client)
            assert simpleRequestBean.client.isRunning()
            return "HELLO"
        }
    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
    @RequestScope
    static class RequestAwareBean implements RequestAware {

        HttpRequest<?> request

        @Override
        void setRequest(HttpRequest<?> request) {
            this.request = request
        }
    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
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

    @Requires(property = "spec.name", value = "RequestScopeSpec")
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

        @Get("/test-request-scope-stream")
        InputStream testStream() {
            return new ByteArrayInputStream(messageService.message.getBytes(StandardCharsets.UTF_8))
        }

        @Get("/test-request-aware")
        String testAware(HttpRequest request) {
            if (requestAwareBean.request == request) {
                return "OK"
            }
            throw new IllegalStateException("Request does not match")
        }
    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
    @Singleton
    static class ReqTerminatedListener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        int callCount

        @Override
        void onApplicationEvent(HttpRequestTerminatedEvent event) {
            callCount++
        }
    }

    @Requires(property = "spec.name", value = "RequestScopeSpec")
    @Singleton
    static class ReqReceivedListener implements ApplicationEventListener<HttpRequestReceivedEvent> {
        int callCount

        @Override
        void onApplicationEvent(HttpRequestReceivedEvent event) {
            callCount++
        }
    }
}
