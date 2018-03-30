package io.micronaut.management.endpoint.info

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.management.info.source.InfoSource
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.junit.Ignore
import org.reactivestreams.Publisher
import spock.lang.Specification

import javax.inject.Singleton

class InfoEndpointSpec extends Specification {

    void "test the info endpoint returns expected result"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['info.test': 'foo'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().test == "foo"
    }


    @Ignore //TODO: Passes randomly due to lost order of property sources
    void "test ordering of info sources"() {

        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['info.ordered': 'second'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().ordered == "first"

    }

    @Singleton
    static class TestingInfoSource implements InfoSource {

        @Override
        Publisher<PropertySource> getSource() {
            return Flowable.just(new MapPropertySource("foo", [ordered: 'first']))
        }

        @Override
        int getOrder() {
            return HIGHEST_PRECEDENCE
        }
    }

}

