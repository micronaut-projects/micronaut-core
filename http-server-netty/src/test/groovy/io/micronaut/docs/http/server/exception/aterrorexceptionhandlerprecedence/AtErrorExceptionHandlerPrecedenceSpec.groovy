package io.micronaut.docs.http.server.exception.aterrorexceptionhandlerprecedence

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AtErrorExceptionHandlerPrecedenceSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': AtErrorExceptionHandlerPrecedenceSpec.simpleName
    ], Environment.TEST)

    @AutoCleanup
    @Shared
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "@Error global has precedence over ExceptionHandler"() {
        when: 'Exception Handler responds 0, @Error global responds -1'
        HttpRequest request = HttpRequest.GET('/books/stock/1234')
        Integer stock = client.toBlocking().retrieve(request, Integer)

        then: '-1 is received'
        noExceptionThrown()
        stock != null
        stock == -1
    }
}
