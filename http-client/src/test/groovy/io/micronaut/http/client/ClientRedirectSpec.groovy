package io.micronaut.http.client

import edu.umd.cs.findbugs.annotations.Nullable
import groovy.transform.NotYetImplemented
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientRedirectSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test - client: full uri, direct"() {
        given:
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/test/direct', String)

        then:
        response.status() == HttpStatus.OK
        response.body() == "It works!"

        cleanup:
        client.stop()
        client.close()
    }

    void "test - client: full uri, redirect: absolute - follows correctly"() {
        given:
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/test/redirect', String)

        then: "Micronaut Client follows the redirect to /test/direct"
        response.status() == HttpStatus.OK
        response.body() == "It works!"

        cleanup:
        client.stop()
        client.close()
    }

    @NotYetImplemented
    void "test - client: full uri, redirect: relative"() {
        given:
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/test/redirect-relative', String)

        then: "Client should correctly redirect relatively to /test/direct the same way as "
        "# curl localhost:17320/test/redirect-relative -vvv -L"
        response.status() == HttpStatus.OK
        response.body() == "It works!"

        cleanup:
        client.close()
    }

    void "test - client: relative uri, direct"() {
        given:
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, new LoadBalancer() {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                URL url = embeddedServer.getURL()
                Publishers.just(ServiceInstance.of(url.getHost(), url))
            }

            @Override
            Optional<String> getContextPath() {
                Optional.of("/test")
            }
        })

        when:
        HttpResponse<String> response = client.toBlocking().exchange('direct', String)

        then:
        response.status() == HttpStatus.OK
        response.body() == "It works!"

        cleanup:
        client.stop()
        client.close()
    }

    void "test - client: relative uri - no slash"() {
        given:
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, new LoadBalancer() {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                URL url = embeddedServer.getURL()
                Publishers.just(ServiceInstance.of(url.getHost(), url))
            }

            @Override
            Optional<String> getContextPath() {
                Optional.of("test")
            }
        })
        when:
        HttpResponse<String> response = client.toBlocking().exchange('direct', String)

        then: "Client is supposed to issue 'GET /test/direct HTTP/1.1' but instead it does 'GET test/direct HTTP/1.1' which fails"
        response.status() == HttpStatus.OK
        response.body() == "It works!"

        cleanup:
        client.stop()
        client.close()
    }

    void "test - client: relative uri, redirect: absolute "() {
        given:
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, new LoadBalancer() {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                URL url = embeddedServer.getURL()
                Publishers.just(ServiceInstance.of(url.getHost(), url))
            }

            @Override
            Optional<String> getContextPath() {
                Optional.of("/test")
            }
        })

        when:
        HttpResponse<String> response = client.toBlocking().exchange('redirect', String)

        then: "Client is supposed to redirect and call '/test/direct' but it incorrectly calls '/test/test/direct'"
        response.status() == HttpStatus.OK
        response.body() == "It works!"

        cleanup:
        client.stop()
        client.close()
    }

    @Controller('/test')
    static class StreamController {

        @Get("/redirect")
        HttpResponse redirect() {
            return HttpResponse.redirect(URI.create("/test/direct"))
        }

        @Get("/redirect-relative")
        HttpResponse redirectRelative() {
            return HttpResponse.redirect(URI.create("./direct"))
        }

        @Get("/direct")
        @Produces("text/plain")
        HttpResponse direct() {
            return HttpResponse.ok("It works!")
        }
    }

}
