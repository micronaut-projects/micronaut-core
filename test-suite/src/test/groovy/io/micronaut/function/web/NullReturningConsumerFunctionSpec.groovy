package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.function.FunctionBean
import io.micronaut.function.client.FunctionClient
import io.micronaut.http.annotation.Body
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named
import spock.lang.Issue
import spock.lang.Specification

import java.util.function.Consumer

class NullReturningConsumerFunctionSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2024")
    def "test a client with a void return type"() {
        given:
        def embeddedServer = ApplicationContext.run(EmbeddedServer)
        def client = embeddedServer.applicationContext.getBean(MicronautFunctionProblemsClient)

        when:
        client.sendVoid("woo")

        then:
        PojoConsumer.LAST_VALUE == "woo"
    }

    @FunctionClient
    static interface MicronautFunctionProblemsClient {

        @Named("consumer/void")
        void sendVoid(String message)
    }

    @FunctionBean("consumer/void")
    static class PojoConsumer implements Consumer<String> {

        static String LAST_VALUE

        @Override
        void accept(@Body String book) {
            LAST_VALUE = book
        }
    }
}
