package io.micronaut.http.server.netty.stream

import com.fasterxml.jackson.databind.JsonNode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
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
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InputStreamBodySpec2 extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['spec.name': InputStreamBodySpec2.class.name,
             'micronaut.http.client.read-timeout': '30s',
             'micronaut.netty.event-loops.default.num-threads': '1'])

    @Retry
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6100')
    @Ignore("this test is super flakey and I'm not sure why")
    void "test apply load to InputStream read"() {
        given:
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURI())

        when:
        int max = 1
        CountDownLatch latch = new CountDownLatch(max)
        List<Throwable> errors = []
        ExecutorService pool = Executors.newCachedThreadPool()
        ConcurrentLinkedQueue<HttpStatus> responses = new ConcurrentLinkedQueue()
        byte[] body = ("largefile" * 1024 * 1024).bytes
        for (int i = 0; i < max; i++) {
            pool.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.POST("/input-stream-test/hello", body)
                    HttpResponse response = client.toBlocking()
                            .exchange(request, JsonNode)
                    def len = response.body.get().get("payload").get("length").asInt()
                    if (len != 9 * 1024 * 1024) {
                        throw new RuntimeException("FAIL: wrong length: $len")
                    }
                    responses.add(response.status())
                    System.out.println(response.getStatus())
                    System.out.println(response.getHeaders().asMap())
                } catch (Throwable e) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            })
        }
        latch.await()

        then:
        errors.isEmpty()
        responses.size() == max
        responses.every({ it == HttpStatus.OK})
    }

    @Requires(property = "spec.name", value = "io.micronaut.http.server.netty.stream.InputStreamBodySpec2")
    @Controller("/input-stream-test")
    static class PayloadInputStream {

        @Inject
        @Client("/")
        private HttpClient httpClient

        @Post("/hello")
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.ALL)
        @ExecuteOn(TaskExecutors.IO)
        MutableHttpResponse<String> stream(@Body @Nullable InputStream payload) throws IOException {

            long n = 0;
            def b = new byte[1024 * 1024]
            while (true) {
                //blocking read on injected http client
                try {
                    HttpResponse<String> resp = httpClient.toBlocking().exchange(HttpRequest.GET(URI.create("/input-stream-test/hello/other")), String.class)
                } catch (ReadTimeoutException e) {
                    println 'RTE inner'
                    e.printStackTrace()
                }

                def here = payload.read(b)
                if (here == -1) break
                n += here
            }
            payload.close()

            String responsePayload = "{\"payload\" : {\"length\" : $n}}"
            return HttpResponse.ok().body(responsePayload).contentType(MediaType.APPLICATION_JSON)
        }

        @Get("/hello/other")
        String other() {
            return "Some body content"
        }
    }
}
