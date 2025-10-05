package io.micronaut.redirect


import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class SimpleControllerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void simple() {
        expect:
            "index" == client.toBlocking().retrieve("/")
    }

    void echo(String path) {
        expect:
            "{}" == client.toBlocking().retrieve(HttpRequest.POST(path, "{}"))
            """{"foo":"bar"}""" == client.toBlocking().retrieve(HttpRequest.POST(path, """{"foo":"bar"}"""))
        where:
            path << [
                    SimpleController.ECHO_PUBLISHER,
                    SimpleController.ECHO_STRING_PUBLISHER,
                    SimpleController.ECHO_MONO,
                    SimpleController.ECHO_FLUX,
                    SimpleController.ECHO_FUTURE,
                    SimpleController.ECHO_ARRAY,
                    SimpleController.ECHO_STRING,
                    RedirectingController.REDIRECT_PUBLISHER,
                    RedirectingController.REDIRECT_STRING_PUBLISHER,
                    RedirectingController.REDIRECT_MONO,
                    RedirectingController.REDIRECT_FLUX,
                    RedirectingController.REDIRECT_FUTURE,
                    RedirectingController.REDIRECT_ARRAY,
                    RedirectingController.REDIRECT_STRING,
            ]
    }

    void echoPiece() {
        expect:
            "bar" == client.toBlocking().retrieve(HttpRequest.POST(SimpleController.ECHO_PIECE_JSON, "{\"foo\": \"bar\"}"))
            "bar" == client.toBlocking().retrieve(HttpRequest.POST(SimpleController.ECHO_PIECE_JSON, "foo=bar").contentType(MediaType.APPLICATION_FORM_URLENCODED))
    }

    void redirectPiece() {
        expect:
            "bar" == client.toBlocking().retrieve(HttpRequest.POST(RedirectingController.REDIRECT_PIECE_JSON, "{\"foo\": \"bar\"}"))
    }
}
