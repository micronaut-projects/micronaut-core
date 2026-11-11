package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import io.micronaut.context.annotation.Property

@MicronautTest
@Property(name = 'spec.name', value = 'AcceptLanguageLengthSpec')
class AcceptLanguageLengthSpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    void 'an over-long Accept-Language tag resolves to the default locale'() {
        given:
        String variants = (1..100).collect { String.format('v%04d', it) }.join('-')

        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/accept-language').header('Accept-Language', 'en-US-' + variants + ';q=0.9')) == Locale.getDefault().toLanguageTag()
        client.toBlocking().retrieve(HttpRequest.GET('/accept-language').header('Accept-Language', 'de-CH-1996;q=0.9, en')) == 'de-CH-1996'
    }

    @Requires(property = 'spec.name', value = 'AcceptLanguageLengthSpec')
    @Controller
    static class LocaleController {
        @Get('/accept-language')
        String locale(HttpRequest<?> request) {
            request.headers.findAcceptLanguage().orElseThrow().toLanguageTag()
        }
    }
}
