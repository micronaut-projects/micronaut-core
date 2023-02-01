package io.micronaut.http.client.javanet

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.ClientSslConfiguration
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException
import java.security.GeneralSecurityException

class SslSpec extends Specification {

    void 'bad server ssl cert'() {
        given:
        def client = HttpClient.create(new URL(url))

        when:
        client.toBlocking().exchange('/')

        then:
        def e = thrown RuntimeException
        e.printStackTrace()
        e.cause instanceof GeneralSecurityException || e.cause instanceof SSLHandshakeException

        cleanup:
        client.stop()

        where:
        url << [
                'https://expired.badssl.com/',
                'https://wrong.host.badssl.com/',
                'https://self-signed.badssl.com/',
                'https://untrusted-root.badssl.com/',
                'https://revoked.badssl.com/',
                'https://no-subject.badssl.com/',
                'https://reversed-chain.badssl.com/',
                'https://rc4-md5.badssl.com/',
                'https://rc4.badssl.com/',
                'https://3des.badssl.com/',
                'https://null.badssl.com/',
                'https://dh480.badssl.com/',
                'https://dh512.badssl.com/',
                // TODO: These currently fail to throw an exception, or hang
                //'https://pinning-test.badssl.com/', passes
                //'https://dh1024.badssl.com/', currently hangs...
                //'https://dh-small-subgroup.badssl.com/', passes
                //'https://dh-composite.badssl.com/', currently hangs...
        ]
    }

    void 'self-signed allowed with config'() {
        given:
        def cfg = new DefaultHttpClientConfiguration()
        ((ClientSslConfiguration) cfg.getSslConfiguration()).setInsecureTrustAllCertificates(true)
        def client = HttpClient.create(new URL('https://self-signed.badssl.com/'), cfg)

        when:
        client.toBlocking().exchange('/')

        then:
        noExceptionThrown()

        cleanup:
        client.stop()
    }

    void 'normal ssl host allowed'() {
        given:
        def client = HttpClient.create(new URL('https://www.google.com/'))

        when:
        client.toBlocking().exchange('/')

        then:
        noExceptionThrown()

        cleanup:
        client.stop()
    }
}
