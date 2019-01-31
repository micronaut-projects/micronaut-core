package io.micronaut.views.model.security

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class SecurityViewModelProcessorSpec extends Specification {

    def "if micronaut.security.views-model-decorator.enabled=true SecurityViewsModelDecorator bean exists"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.views-model-decorator.enabled': false,
        ])

        expect:
        !applicationContext.containsBean(SecurityViewModelProcessor)

        cleanup:
        applicationContext.close()
    }

    def "by default SecurityViewsModelDecorator bean exists"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.security.enabled': true,
        ])

        expect:
        applicationContext.containsBean(SecurityViewModelProcessor)

        cleanup:
        applicationContext.close()
    }

    def "a custom security property name can be injected to the model"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'SecurityViewModelProcessorSpec',
                'micronaut.security.enabled': true,
                'micronaut.security.views-model-decorator.security-key': 'securitycustom',
                'micronaut.views.handlebars.enabled': false,
                'micronaut.views.thymeleaf.enabled': false,
                'micronaut.views.velocity.enabled': true,
                'micronaut.views.freemarker.enabled': false,
        ])
        HttpClient httpClient = HttpClient.create(embeddedServer.URL)

        expect:
        embeddedServer.applicationContext.containsBean(BooksController)

        and:
        embeddedServer.applicationContext.containsBean(MockAuthenticationProvider)

        and:
        embeddedServer.applicationContext.containsBean(SecurityViewModelProcessor)

        when:
        HttpRequest request = HttpRequest.GET("/").basicAuth('john', 'secret')
        HttpResponse<String> response = httpClient.toBlocking().exchange(request, String)

        then:
        response.status() == HttpStatus.OK

        when:
        String html = response.body()

        then:
        html

        and:
        !html.contains('User: john')

        and:
        html.contains('Custom: john')

        cleanup:
        httpClient.close()

        and:
        embeddedServer.close()
    }

    def "security property is injected to the model"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'SecurityViewModelProcessorSpec',
                'micronaut.security.enabled': true,
                'micronaut.views.handlebars.enabled': false,
                'micronaut.views.thymeleaf.enabled': false,
                'micronaut.views.velocity.enabled': true,
                'micronaut.views.freemarker.enabled': false,
        ])
        HttpClient httpClient = HttpClient.create(embeddedServer.URL)

        expect:
        embeddedServer.applicationContext.containsBean(BooksController)

        and:
        embeddedServer.applicationContext.containsBean(MockAuthenticationProvider)

        and:
        embeddedServer.applicationContext.containsBean(SecurityViewModelProcessor)

        when:
        HttpRequest request = HttpRequest.GET("/").basicAuth('john', 'secret')
        HttpResponse<String> response = httpClient.toBlocking().exchange(request, String)

        then:
        response.status() == HttpStatus.OK

        when:
        String html = response.body()

        then:
        html.contains('User: john email: john@email.com')

        and:
        html.contains('Developing Microservices')

        cleanup:
        httpClient.close()

        and:
        embeddedServer.close()
    }
}
