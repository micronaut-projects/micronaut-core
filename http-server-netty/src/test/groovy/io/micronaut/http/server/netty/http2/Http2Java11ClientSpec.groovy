package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Specification

import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch

import static io.micronaut.http.MediaType.ALL
import static java.time.Duration.ofSeconds
import static java.util.concurrent.CompletableFuture.supplyAsync

@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.ssl.enabled", value = "true")
@Property(name = "micronaut.ssl.port", value = "-1")
@Property(name = "micronaut.ssl.buildSelfSigned", value = "true")
@Property(name = "micronaut.server.netty.log-level", value = "TRACE")
@Property(name = "micronaut.http.client.log-level", value = "TRACE")
@Requires({ jvm.current.isJava11Compatible() })
class Http2Java11ClientSpec extends Specification {

    @Inject EmbeddedServer embeddedServer

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/4412')
    void 'test post and get with java 11 client'() {
        given:
        // soft loading since we have to compile with Java 8
        def versionClass = loadClass('java.net.http.HttpClient$Version')
        def bodyPublishers = loadClass('java.net.http.HttpRequest$BodyPublishers')
        def bodyHandlers = loadClass('java.net.http.HttpResponse$BodyHandlers')
        def requestClass = loadClass('java.net.http.HttpRequest')
        def httpClient = loadClass('java.net.http.HttpClient')
                .newBuilder()
//             .version(versionClass.HTTP_1_1) // The test will pass if this is uncommented
                .sslContext(createSSLContextThatIgnoresInvalidCertificates())
                .build()
        final untilUploadHasStarted = new CountDownLatch(1)
        final  untilDownloadIsFinished = new CountDownLatch(1)

        when:
        final upload = requestClass
                .newBuilder()
                .uri(URI.create("https://localhost:${embeddedServer.port}/http2/java11/upload"))
                .PUT(bodyPublishers.ofInputStream(() -> new InputStream() {

                    private final Random random = new Random()
                    private int numberOfBytesLeftToWrite = 1024 * 1024

                    @Override
                    int read() throws IOException {
                        if (--numberOfBytesLeftToWrite < 0) {
                            return -1
                        }
                        untilUploadHasStarted.countDown()
                        return random.nextInt(256)
                    }

                }))
                .build()
        final responseToUpload = supplyAsync(() -> {
            try {
                return httpClient.send(upload, bodyHandlers.discarding())
            } catch (exception) {
                throw new RuntimeException(exception)
            }
        }, command -> new Thread(command).start())
        untilUploadHasStarted.await()
        final download = requestClass
                .newBuilder()
                .uri(URI.create("https://localhost:${embeddedServer.port}/http2/java11/download"))
                .GET()
                .timeout(ofSeconds(10))
                .build()

        final responseToDownload = httpClient.send(download, bodyHandlers.ofString())
        untilDownloadIsFinished.countDown()

        then:
        responseToDownload.statusCode() == 200
        responseToDownload.body() == 'applesauce'
        responseToUpload.get().statusCode() == 200
    }

    private Class<?> loadClass(String clientName) {
        getClass().classLoader.loadClass(clientName)
    }

    private SSLContext createSSLContextThatIgnoresInvalidCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        def context = SSLContext.getInstance("TLS")
        context.init(null, new TrustManager[] {
                new X509TrustManager() {

                    @Override
                    void checkClientTrusted(X509Certificate[] chain, String authType) {

                    }

                    @Override
                    void checkServerTrusted(X509Certificate[] chain, String authType) {

                    }

                    @Override
                    X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0]
                    }

                }
        }, new SecureRandom())
        return context
    }

    @Controller('/http2/java11')
    static class ExampleController {

        @Put("/upload")
        @Consumes(ALL)
        void upload(@Body byte[] body) {
            println("uploading")
        }

        @Get("/download")
        String download() {
            return "applesauce"
        }

    }

}
