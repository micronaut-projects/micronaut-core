package io.micronaut.docs.client.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.ClientSslConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

class HostnameVerifierSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                             : 'HostnameVerifierSpec',
            'micronaut.server.ssl.enabled'          : true,
            'micronaut.server.ssl.build-self-signed': true,
            'micronaut.server.ssl.port'             : -1,
    ])

    void 'a verifier accepts the host'() {
        given:
        HttpClient client = client(server.URL, { String host, SSLSession session -> host == 'localhost' })

        expect:
        client.toBlocking().retrieve('/verified') == 'ok'

        cleanup:
        client.close()
    }

    void 'a rejecting verifier fails the request'() {
        given:
        HttpClient client = client(server.URL, { String host, SSLSession session -> false })

        when:
        client.toBlocking().retrieve('/verified')

        then:
        thrown(Exception)

        cleanup:
        client.close()
    }

    private static HttpClient client(URL url, HostnameVerifier verifier) {
        // tag::hostnameVerifier[]
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration()
        ClientSslConfiguration ssl = new ClientSslConfiguration()
        ssl.insecureTrustAllCertificates = true // only for the self-signed test certificate
        ssl.endpointIdentification = false
        ssl.hostnameVerifier = verifier // <1>
        configuration.sslConfiguration = ssl
        HttpClient client = HttpClient.create(url, configuration)
        // end::hostnameVerifier[]
        return client
    }

    @Requires(property = 'spec.name', value = 'HostnameVerifierSpec')
    @Controller
    static class VerifiedController {
        @Get('/verified')
        String get() {
            'ok'
        }
    }
}
