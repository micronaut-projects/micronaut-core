package io.micronaut.http.client.jdk

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.ClientSslConfiguration
import spock.lang.PendingFeature
import spock.lang.Retry
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException
import java.security.GeneralSecurityException
import java.time.Duration

// See http-client/src/test/groovy/io/micronaut/http/client/SslSpec.groovy
class SslSpec extends Specification {

    @Retry(count = 5) // sometimes badssl.com times out
    void 'bad server ssl cert'() {
        given:
        def cfg = new DefaultHttpClientConfiguration()
        cfg.connectTimeout = Duration.ofSeconds(50)
        cfg.readTimeout = Duration.ofSeconds(50)
        def client = HttpClient.create(new URL(url), cfg)

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
                //'https://revoked.badssl.com/', needs jvm option
                //'https://pinning-test.badssl.com/', // not implemented
                'https://no-subject.badssl.com/',
                'https://reversed-chain.badssl.com/',
                'https://rc4-md5.badssl.com/',
                'https://rc4.badssl.com/',
                'https://3des.badssl.com/',
                'https://null.badssl.com/',
                'https://dh480.badssl.com/',
                'https://dh512.badssl.com/',
                // 'https://dh1024.badssl.com/', // passes
                // 'https://dh-small-subgroup.badssl.com/', // passes
                // 'https://dh-composite.badssl.com/', // times out
        ]
    }

    @PendingFeature
    void 'bad server ssl cert currently unsupported'() {
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
                'https://pinning-test.badssl.com/', // not implemented
                'https://dh1024.badssl.com/', // passes
                'https://dh-small-subgroup.badssl.com/', // passes
                'https://dh-composite.badssl.com/', // times out
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
