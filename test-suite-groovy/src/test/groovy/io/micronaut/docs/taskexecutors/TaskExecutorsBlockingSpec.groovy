package io.micronaut.docs.taskexecutors

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.uri.UriBuilder
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@Property(name = "spec.name", value = "TaskExecutorsBlockingTest")
@MicronautTest
class TaskExecutorsBlockingTest {
    @Inject
    @Client("/")
    HttpClient httpClient

    void "test method annotated with TaskExecutors at Blocking"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()
        HttpRequest<?> request = HttpRequest.GET(UriBuilder.of("/hello").path("world").build()).accept(MediaType.TEXT_PLAIN)

        when:
        HttpResponse<?> response = client.exchange(request, String)

        then:
        noExceptionThrown()
        HttpStatus.OK == response.status()

        when:
        Optional<String> txt = response.getBody(String)

        then:
        txt.isPresent()
        "Hello World" == txt.get()
    }
}
