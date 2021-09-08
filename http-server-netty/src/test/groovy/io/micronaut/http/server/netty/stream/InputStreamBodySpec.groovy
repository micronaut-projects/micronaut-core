package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.*
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InputStreamBodySpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6100')
    void "test apply load to InputStream read"() {
        given:
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURI())

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
        responses.every({ it == HttpStatus.OK})
    }
}

@Controller("/input-stream-test")
class PayloadInputStream {

    @Inject
    @Client("/")
    private HttpClient httpClient

    private String responsePayload = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "   <soap:Body>\n" +
            "      <ns2:getHelloWorldAsStringResponse xmlns:ns2=\"http://sample.soap.oracle/\">\n" +
            "         <return>Hello World %s</return>\n" +
            "      </ns2:getHelloWorldAsStringResponse>\n" +
            "   </soap:Body>\n" +
            "</soap:Envelope>"


    @Post("/hello")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.ALL)
    @ExecuteOn(TaskExecutors.IO)
    MutableHttpResponse<String> stream(@Body @Nullable InputStream payload) throws IOException {

        //blocking read on injected http client
        HttpResponse<String> resp = httpClient.toBlocking().exchange(HttpRequest
                .GET(URI.create("/input-stream-test/hello/other")), String.class)
        System.out.println(resp.getBody(String.class).get())


        byte[] body = payload.bytes
        String b = new String(body)
        int l = body.length

        responsePayload = "{\n" +
                "\t\"payload\" : {\n" +
                "\t\t\"name\" : \"1542\"\n" +
                "\t}\n" +
                "}"
        return HttpResponse.ok().body(responsePayload).contentType(MediaType.APPLICATION_JSON)
    }

    @Get("/hello/other")
    String other() {
        return "Some body content"
    }
}