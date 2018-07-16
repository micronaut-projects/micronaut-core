package io.micronaut.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.templates.HandlebarsTemplateRenderer
import io.micronaut.templates.TemplatesFilter
import io.micronaut.templates.ThymeleafTemplateRenderer
import io.micronaut.templates.VelocityTemplateRenderer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ThymeleafTemplateRendererSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': 'thymeleaf',
                    'micronaut.templates.enabled': true,
                    'micronaut.templates.velocity.enabled': false,
                    'micronaut.templates.handlebars.enabled': false,
            ],
            "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

    def "bean is loaded"() {
        when:
        embeddedServer.applicationContext.getBean(TemplatesFilter)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(ThymeleafTemplateRenderer)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(HandlebarsTemplateRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(VelocityTemplateRenderer)

        then:
        thrown(NoSuchBeanException)
    }

    def "invoking /template/home does not specify @Template, thus, regular JSON rendering is used"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/template/home', String)

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

    def "invoking /template renders thymeleaf template from a controller returning a map"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/template', String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        when:
        String body = rsp.body()

        then:
        body
        rsp.body().contains("<h1>username: <span>sdelamo</span></h1>")
    }

    def "invoking /template/pogo renders th template from a controller returning a pogo"() {
        when:
        HttpResponse<String> rsp = client.toBlocking().exchange('/template/pogo', String)

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

    def "invoking /template/bogus returns 404 if you attempt to render a template which does not exist"() {
        when:
        client.toBlocking().exchange('/template/bogus', String)

        then:
        def e = thrown(HttpClientResponseException)

        and:
        e.status == HttpStatus.NOT_FOUND
    }
}
