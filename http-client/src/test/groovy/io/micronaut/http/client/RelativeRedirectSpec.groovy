package io.micronaut.http.client

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.uri.UriBuilder
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "RelativeRedirectSpec")
class RelativeRedirectSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    void "redirect with relative path './target' is resolved correctly"() {
        when:
        String result = httpClient.toBlocking().retrieve("/redirect/relative-dot")

        then:
        result == "OK from /redirect/target"
    }

    void "redirect with relative path '../result' is resolved correctly"() {
        when:
        String result = httpClient.toBlocking().retrieve("/a/b/redirect-up")

        then:
        result == "OK from /a/result"
    }

    void "redirect with root-relative path '/other' is resolved correctly"() {
        when:
        String result = httpClient.toBlocking().retrieve("/redirect/to-root-relative")

        then:
        result == "OK from /other"
    }

    void "redirect with absolute Location URL still works"() {
        when:
        String result = httpClient.toBlocking().retrieve("/redirect/to-absolute")

        then:
        result == "OK from /target/absolute"
    }

    void "multi-hop redirect (relative then root-relative) works"() {
        when:
        String result = httpClient.toBlocking().retrieve("/redirect/chain")

        then:
        result == "OK from /target/final"
    }

    @Controller
    @Requires(property = "spec.name", value = "RelativeRedirectSpec")
    static class RedirectController {

        /** Redirects to ./target (same-directory relative) */
        @Get("/redirect/relative-dot")
        HttpResponse<?> redirectRelativeDot() {
            HttpResponse.temporaryRedirect(URI.create("./target"))
        }

        /** Target endpoint of the ./target redirect */
        @Get("/redirect/target")
        @Produces(MediaType.TEXT_PLAIN)
        String redirectTarget() {
            "OK from /redirect/target"
        }

        /** Redirects to ../result (parent-directory relative) */
        @Get("/a/b/redirect-up")
        HttpResponse<?> redirectUp() {
            HttpResponse.temporaryRedirect(URI.create("../result"))
        }

        /** Target endpoint of ../result redirect */
        @Get("/a/result")
        @Produces(MediaType.TEXT_PLAIN)
        String aResult() {
            "OK from /a/result"
        }

        /** Redirects to /other (root-relative path) */
        @Get("/redirect/to-root-relative")
        HttpResponse<?> redirectRootRelative() {
            HttpResponse.temporaryRedirect(URI.create("/other"))
        }

        @Get("/other")
        @Produces(MediaType.TEXT_PLAIN)
        String other() {
            "OK from /other"
        }

        /**
         * Redirects to an absolute URL.
         * Reconstructs the full URL from the incoming request so the
         * embedded test server can handle the redirect internally.
         */
        @Get("/redirect/to-absolute")
        HttpResponse<?> redirectToAbsolute(HttpRequest<?> request) {
            URI target = UriBuilder.of(request.getUri())
                    .replacePath("/target/absolute")
                    .build()
            HttpResponse.temporaryRedirect(target)
        }

        @Get("/target/absolute")
        @Produces(MediaType.TEXT_PLAIN)
        String targetAbsolute() {
            "OK from /target/absolute"
        }

        /** Hop 1: redirects to ./step2 (relative) */
        @Get("/redirect/chain")
        HttpResponse<?> redirectChain() {
            HttpResponse.temporaryRedirect(URI.create("./step2"))
        }

        /** Hop 2: redirects to /target/final (root-relative) */
        @Get("/redirect/step2")
        HttpResponse<?> redirectStep2() {
            HttpResponse.temporaryRedirect(URI.create("/target/final"))
        }

        @Get("/target/final")
        @Produces(MediaType.TEXT_PLAIN)
        String targetFinal() {
            "OK from /target/final"
        }
    }
}
