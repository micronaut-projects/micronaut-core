package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import jakarta.annotation.Nullable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InputStreamFullBodySpec  extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': InputStreamBodySpec.class.name,
            "micronaut.server.netty.server-type": NettyHttpServerConfiguration.HttpServerType.FULL_CONTENT
    ])
    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURI())

    void "test nullable body"() {
        when:
        def response = client.toBlocking()
                .exchange(HttpRequest.POST("/input-stream-test/hello", null))

        then:
        response.status() == HttpStatus.NO_CONTENT
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6100')
    void "test apply load to InputStream read"() {
        when:
        int max = 30
        CountDownLatch latch = new CountDownLatch(max)

        ExecutorService pool = Executors.newCachedThreadPool()
        ConcurrentLinkedQueue<HttpStatus> responses = new ConcurrentLinkedQueue()
        for (int i = 0; i < max; i++) {
            pool.submit(() -> {
                try {
                    MultipartBody multipartBody = MultipartBody.builder()
                            .addPart("myfile",
                                    "largefile" * 1024)
                            .build()
                    HttpRequest request = HttpRequest.POST("/input-stream-test/hello", multipartBody)
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    HttpResponse response = client.toBlocking()
                            .exchange(request)
                    responses.add(response.status())
                    System.out.println(response.getStatus())
                    System.out.println(response.getHeaders().asMap())

                } catch (HttpClientResponseException e) {
                    System.out.println(e.getStatus())
                } catch (URISyntaxException e) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }

            })
        }
        latch.await()

        then:
        responses.size() == 30
        responses.every({ it == HttpStatus.OK })
    }

}
