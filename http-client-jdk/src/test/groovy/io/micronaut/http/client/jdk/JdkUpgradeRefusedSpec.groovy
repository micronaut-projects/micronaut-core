package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.client.exceptions.HttpClientException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The JDK client gives no access to a connection that switched protocols, so a request that
 * asks to upgrade fails before it is sent when upgrades are allowed.
 */
class JdkUpgradeRefusedSpec extends Specification {

    void "an upgrade request fails before it is sent"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        RawHttpClient client = ctx.createBean(RawHttpClient)
        CloseableByteBody body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
                .adapt('payload'.getBytes(StandardCharsets.UTF_8))
        def request = HttpRequest.POST('http://127.0.0.1:1/echo', null)
                .header('Connection', 'Upgrade')
                .header('Upgrade', 'echo')

        when:
        Mono.from(client.exchange(request, body, null, RawRequestOptions.proxy())).block()

        then:
        def e = thrown(HttpClientException)
        e.message.contains("'echo'")

        cleanup:
        client.close()
        ctx.close()
    }
}
