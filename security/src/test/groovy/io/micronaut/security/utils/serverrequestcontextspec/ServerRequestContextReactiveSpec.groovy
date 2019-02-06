package io.micronaut.security.utils.serverrequestcontextspec

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification

class ServerRequestContextReactiveSpec extends Specification {

    def "verifies ServerRequestContext.currentRequest() does not return null for reactive flows"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ServerRequestContextReactiveSpec',
                'micronaut.security.enabled': true,
        ])

        expect:
        embeddedServer.applicationContext.containsBean(MyController)

        when:
        RxHttpClient httpClient = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.URL)
        Flowable<Message> messages = httpClient.retrieve(HttpRequest.GET("/mycontroller/simple"), Message)

        then:
        messages

        when:
        Message message = messages.blockingFirst()

        then:
        message
        message.message == 'Sergio'

        when:
        messages = httpClient.retrieve(HttpRequest.GET("/mycontroller"), Message)

        then:
        messages

        when:
        message = messages.blockingFirst()

        then:
        message
        message.message == 'Sergio'

        cleanup:
        embeddedServer.close()
    }
}
