package io.micronaut.http.server.netty.util

import io.micronaut.context.ApplicationContext
import io.micronaut.context.LocalizedMessageSource
import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpLocalizedMessageSourceSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HttpLocalizedMessageSourceSpec',
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Shared
    @AutoCleanup
    BlockingHttpClient client = httpClient.toBlocking()

    void "depending on the resolved locale default messages.properties or default messages_es.properties are used"() {
        expect:
        "Hello Welcome Sergio Good Bye" == client.retrieve(HttpRequest.GET('/i18n').header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        "Hola Bienvenido Sergio Good Bye" == client.retrieve(HttpRequest.GET('/i18n').header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
        "Hello Welcome Sergio Good Bye" == client.retrieve(HttpRequest.GET('/i18n/default').header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        "Hola Bienvenido Sergio Good Bye" == client.retrieve(HttpRequest.GET('/i18n/default').header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
    }

    @Requires(property = "spec.name", value = "HttpLocalizedMessageSourceSpec")
    @Factory
    static class MessageSourceFactory {
        @Singleton
        MessageSource createMessageSource() {
            return new ResourceBundleMessageSource("i18n.messages");
        }
    }

    @Requires(property = "spec.name", value = "HttpLocalizedMessageSourceSpec")
    @Controller("/i18n")
    static class LocalizedMessageSourceController {
        private final LocalizedMessageSource localizedMessageSource;

        LocalizedMessageSourceController(LocalizedMessageSource localizedMessageSource) {
            this.localizedMessageSource = localizedMessageSource
        }

        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String message() {
            return localizedMessageSource.getMessage("hello").get() + " " +
            localizedMessageSource.getMessage("welcome.name" , "Sergio").get() + " " +
            localizedMessageSource.getMessageOrDefault("bye" , "Good Bye")
        }

        @Get("/default")
        @Produces(MediaType.TEXT_PLAIN)
        String messageOrDefault() {
            return localizedMessageSource.getMessageOrDefault("hello", "Foo") + " " +
                    localizedMessageSource.getMessageOrDefault("welcome.name" , "Foo", "Sergio") + " " +
                    localizedMessageSource.getMessageOrDefault("bye" , "Good Bye", ["foo": "bar"])
        }
    }
}
