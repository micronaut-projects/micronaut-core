package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.ArrayUtils
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.Jvm

import java.nio.charset.StandardCharsets

class OctetStreamSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.max-request-size': '10KB',
                                                                            'spec.name': 'OctetStreamSpec',])

    @Shared
    UploadClient client = embeddedServer.applicationContext.getBean(UploadClient)

    void "test exchange byte[] blocking"() {
        given:
        def data = new String("xyz" * 500).bytes

        expect:
        new String(client.byteArray(data), StandardCharsets.UTF_8) == new String(data, StandardCharsets.UTF_8)
    }

    void "test exchange byte[] blocking - too big"() {
        given:
        def data = new String("xyz" * 50000).bytes

        when:
        new String(client.byteArray(data), StandardCharsets.UTF_8) == new String(data, StandardCharsets.UTF_8)

        then:
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == 'The content length [150000] exceeds the maximum allowed content length [10240]'
    }

    void "test exchange byte[] non blocking"() {
        given:
        def data = new String("xyz" * 500).bytes

        expect:
        new String(Flux.from(client.byteArrayFlowable(Flux.just(data))).reduce({ byte[] a, byte[] b -> ArrayUtils.concat(a, b)}).block(), StandardCharsets.UTF_8) == new String(data, StandardCharsets.UTF_8)
    }

    // TODO: Investigate why this fails on JDK 11
    @IgnoreIf({ Jvm.current.isJava9Compatible() })
    void "test exchange byte[] non blocking - too big"() {
        given:
        def data = new String("xyz" * 100000).bytes

        when:
        new String(Flux.from(client.byteArrayFlowable(Flux.just(data))).reduce({ byte[] a, byte[] b -> ArrayUtils.concat(a, b) }).block(), StandardCharsets.UTF_8)

        then:"Cannot compute ahead of time the content length so use the received amount, also streamed responses that fail in the middle result in connection reset exception"
        thrown(RuntimeException)
    }

    @Requires(property = 'spec.name', value = 'OctetStreamSpec')
    @Client('/binary')
    static interface UploadClient {

        @Post(processes = MediaType.APPLICATION_OCTET_STREAM, uri = '/byte-array')
        byte[] byteArray(@Body byte[] byteArray)

        @Post(processes = MediaType.APPLICATION_OCTET_STREAM, uri = '/byte-array-flowable')
        Publisher<byte[]> byteArrayFlowable(@Body Publisher<byte[]> byteArray)
    }

    @Requires(property = 'spec.name', value = 'OctetStreamSpec')
    @Controller("/binary")
    static class UploadController {

        @Post(processes = MediaType.APPLICATION_OCTET_STREAM, uri = '/byte-array')
        byte[] byteArray(@Body byte[] byteArray) {
            return byteArray
        }

        @Post(processes = MediaType.APPLICATION_OCTET_STREAM, uri = '/byte-array-flowable')
        Publisher<byte[]> byteArrayFlowable(@Body Publisher<byte[]> byteArray) {
            return byteArray
        }
    }
}
