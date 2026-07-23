package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Issue
import spock.lang.Specification

@MicronautTest
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/10263')
class JsonBodyArgumentSpreadingSpec extends Specification implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        ['spec.name': 'JsonBodyArgumentSpreadingSpec']
    }

    @jakarta.inject.Inject
    @Client('/')
    HttpClient client

    void 'json body argument spreading binds nullable parameter to null'() {
        when:
        String rsp = client.toBlocking().retrieve(HttpRequest.POST('/spread', [:]).contentType(MediaType.APPLICATION_JSON))

        then:
        rsp == '{}'
    }

    void 'json body argument spreading with explicit body component binds missing field to null'() {
        when:
        String rsp = client.toBlocking().retrieve(HttpRequest.POST('/spread-annotated', [:]).contentType(MediaType.APPLICATION_JSON))

        then:
        rsp == 'null'
    }

    @Controller
    @Requires(property = 'spec.name', value = 'JsonBodyArgumentSpreadingSpec')
    static class Ctrl {
        @Post('/spread')
        @Consumes(MediaType.APPLICATION_JSON)
        String spread(@Body @jakarta.annotation.Nullable String q) {
            // if binding fails, request will error out before reaching controller
            return String.valueOf(q)
        }

        @Post('/spread-annotated')
        @Consumes(MediaType.APPLICATION_JSON)
        String spreadAnnotated(@Body('q') @jakarta.annotation.Nullable String q) {
            return String.valueOf(q)
        }
    }
}
