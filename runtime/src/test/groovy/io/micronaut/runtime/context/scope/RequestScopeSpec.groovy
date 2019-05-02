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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.inject.BeanDefinition
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.support.AbstractBeanDefinitionSpec

import javax.inject.Inject
import javax.inject.Scope
import javax.inject.Singleton

/**
 * @author Marcel Overdijk
 * @since 1.2
 */
class RequestScopeSpec extends AbstractBeanDefinitionSpec {

    void "test parse bean definition data"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.RequestBean", """
package test;

import io.micronaut.runtime.context.scope.*;

@Request
class RequestBean {

}
""")

        then:
        beanDefinition.getAnnotationNameByStereotype(Scope).get() == Request.name
    }

    void "test bean definition data"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(Environment.TEST)
        BeanDefinition aDefinition = applicationContext.getBeanDefinition(RequestBean)

        expect:
        aDefinition.getAnnotationNameByStereotype(Scope).isPresent()
        aDefinition.getAnnotationNameByStereotype(Scope).get() == Request.name
    }

    void "test bean created per request"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(Environment.TEST)
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer).start()
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def result = client.retrieve(HttpRequest.GET("/test"), String).blockingFirst()

        then:
        result == "message count 1, count within request 1"

        when:
        result = client.retrieve(HttpRequest.GET("/test"), String).blockingFirst()

        then:
        result == "message count 2, count within request 1"

        when:
        result = client.retrieve(HttpRequest.GET("/test"), String).blockingFirst()

        then:
        result == "message count 3, count within request 1"

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

        @Get("/test")
        String test() {
            return messageService.message
        }
    }
}
