package io.micronaut.security.atsecured

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.SecuredInterceptor
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AtSecuredAppliedToServiceMethodSpec extends Specification {
    static final String SPEC_NAME_PROPERTY = 'spec.name'
    static  final String controllerPath = "/atsecured"

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY): AtSecuredAppliedToServiceMethodSpec.class.simpleName,
            'micronaut.security.enabled': true,
            ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "AtSecuredAppliedToServiceMethodSpec collaborators are loaded"() {
        when:
        embeddedServer.applicationContext.getBean(BookController)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(BookRepository)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(SecuredInterceptor)

        then:
        noExceptionThrown()
    }

    def "sherlock can fetch books because he passes @Secured at BookController and BookRepository"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/books")
                .basicAuth("sherlock", "password"))

        then:
        noExceptionThrown()
    }

    def "watson cannot fetch books because he passes @Secured at BookController but fails at BookRepository"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/books")
                .basicAuth("watson", "password"))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status() == HttpStatus.FORBIDDEN
    }
}
