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
package io.micronaut.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.views.handlebars.HandlebarsViewsRenderer
import io.micronaut.views.ViewsFilter
import io.micronaut.views.thymeleaf.ThymeleafViewsRenderer
import io.micronaut.views.velocity.VelocityViewsRenderer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ThymeleafViewRendererSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': 'thymeleaf',
                    'micronaut.views.velocity.enabled': false,
                    'micronaut.views.handlebars.enabled': false,
                    'micronaut.views.freemarker.enabled': false,
            ],
            "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

    def "bean is loaded"() {
        when:
        embeddedServer.applicationContext.getBean(ViewsFilter)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(ThymeleafViewsRenderer)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(HandlebarsViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(VelocityViewsRenderer)

        then:
        thrown(NoSuchBeanException)
    }

    def "invoking /views/home does not specify @View, thus, regular JSON rendering is used"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/views/home', String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK
        rsp.body()

        when:
        String body = rsp.body()

        then:
        body
        rsp.body().contains("{\"username\":\"sdelamo\",\"loggedIn\":true}")
        rsp.contentType.isPresent()
        rsp.contentType.get() == MediaType.APPLICATION_JSON_TYPE
    }

    def "invoking /views renders thymeleaf template from a controller returning a map"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/views', String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        when:
        String body = rsp.body()

        then:
        body
        rsp.body().contains("<h1>username: <span>sdelamo</span></h1>")
    }

    def "invoking /views/pogo renders th template from a controller returning a pogo"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/views/pogo', String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        when:
        String body = rsp.body()

        then:
        body
        rsp.body().contains("<h1>username: <span>sdelamo</span></h1>")

        and: "check you an use thymeleaf templates"
        rsp.body().contains("Layout footer")
    }

    def "invoking /views/bogus returns 404 if you attempt to render a template which does not exist"() {
        when:
        client.toBlocking().exchange('/views/bogus', String)

        then:
        def e = thrown(HttpClientResponseException)

        and:
        e.status == HttpStatus.NOT_FOUND
    }

    def "invoking /views/nullbody renders view even if the response body is null"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/views/nullbody', String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        when:
        String body = rsp.body()

        then:
        body
        rsp.body().contains("<h1>You are not logged in</h1>")
    }

    void "invoking /notFound renders the view defined on the error handler"() {
        when:
        client.toBlocking().exchange(HttpRequest.POST('/views/error', [status: true, exception: false, global: false]), String)

        then:
        def e = thrown(HttpClientResponseException)

        and:
        e.status == HttpStatus.NOT_FOUND
        e.response.getBody(String).get().contains("<h1>I'm sorry, <span>sdelamo</span>! You've reached a dead end. Status: <span>404</span>. Exception: <span></span></h1>")

        when:
        client.toBlocking().exchange(HttpRequest.POST('/views/error', [status: true, exception: false, global: true]), String)

        then:
        e = thrown(HttpClientResponseException)

        and:
        e.status == HttpStatus.NOT_FOUND
        e.response.getBody(String).get().contains("<h1>I'm sorry, <span>sdelamo</span>! You've reached a dead end. Status: <span>420</span>. Exception: <span></span></h1>")

        when:
        client.toBlocking().exchange(HttpRequest.POST('/views/error', [status: false, exception: true, global: false]), String)

        then:
        e = thrown(HttpClientResponseException)

        and:
        e.status == HttpStatus.NOT_FOUND
        e.response.getBody(String).get().contains("<h1>I'm sorry, <span>sdelamo</span>! You've reached a dead end. Status: <span></span>. Exception: <span>local</span></h1>")

        when:
        client.toBlocking().exchange(HttpRequest.POST('/views/error', [status: false, exception: true, global: true]), String)

        then:
        e = thrown(HttpClientResponseException)

        and:
        e.status == HttpStatus.NOT_FOUND
        e.response.getBody(String).get().contains("<h1>I'm sorry, <span>sdelamo</span>! You've reached a dead end. Status: <span></span>. Exception: <span>global</span></h1>")
        
    }

    def "invoking /views/relative-link renders thymeleaf template with relative link"() {
        when:
            HttpResponse<String> rsp = client.toBlocking().exchange('/views/relative-link', String)

        then:
            noExceptionThrown()
            rsp.status() == HttpStatus.OK

        when:
            String body = rsp.body()

        then:
            body
            rsp.body().contains("<a href=\"/views/relative-link/to-resolve\">Relative Link</a>")
    }

    def "invoking /views/relative-link/ renders thymeleaf template with relative link"() {
        when:
            HttpResponse<String> rsp = client.toBlocking().exchange('/views/relative-link/', String)

        then:
            noExceptionThrown()
            rsp.status() == HttpStatus.OK

        when:
            String body = rsp.body()

        then:
            body
            rsp.body().contains("<a href=\"/views/relative-link/to-resolve\">Relative Link</a>")
    }
}
