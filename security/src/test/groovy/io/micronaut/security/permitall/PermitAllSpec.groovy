package io.micronaut.security.permitall

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PermitAllSpec extends Specification {
    static final String SPEC_NAME_PROPERTY = 'spec.name'

    public static final String controllerPath = '/permitall'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY): PermitAllSpec.class.simpleName,
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "PermitAllSpec collaborators are loaded"() {
        when:
        embeddedServer.applicationContext.getBean(BookController)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(LanguagesController)

        then:
        noExceptionThrown()
    }

    def "@PermitAll annotation is equivalent to @Secured('isAnonymous()')"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/books"))

        then:
        noExceptionThrown()
    }

    def "@PermitAll annotation at class level is inherited by methods"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/languages"))

        then:
        noExceptionThrown()
    }
}
