package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.ServiceHttpClientConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class JdkConfigurationSpec extends Specification {
    void 'JDK settings bind in their namespace and services inherit defaults'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.http.client.jdk.apply-request-timeout': true,
            'micronaut.http.client.jdk.use-micronaut-redirects': true,
            'micronaut.http.client.jdk.decode-error-type': true,
            'micronaut.http.services.backend.url': 'http://localhost',
            'micronaut.http.services.backend.jdk.apply-request-timeout': false,
            'micronaut.http.services.other.url': 'http://localhost'
        ])

        expect:
        with(context.getBean(DefaultJdkHttpClientConfiguration).jdk) {
            applyRequestTimeout
            useMicronautRedirects
            decodeErrorType
        }
        with(((JdkHttpClientConfiguration) context.getBean(ServiceHttpClientConfiguration, Qualifiers.byName('backend'))).jdk) {
            !applyRequestTimeout
            useMicronautRedirects
            decodeErrorType
        }
        with(((JdkHttpClientConfiguration) context.getBean(ServiceHttpClientConfiguration, Qualifiers.byName('other'))).jdk) {
            applyRequestTimeout
            useMicronautRedirects
            decodeErrorType
        }

        cleanup:
        context.close()
    }

    void 'the JDK configuration shares the default client settings and is not the primary configuration'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.http.client.read-timeout': '7s',
            'micronaut.http.client.pool.max-concurrent-http1-connections': 7,
            'micronaut.http.client.http2.ping-interval-read': '7s',
            'micronaut.http.client.jdk.decode-error-type': true,
        ])

        when:
        def jdk = context.getBean(DefaultJdkHttpClientConfiguration)

        then:
        context.getBean(HttpClientConfiguration).class == DefaultHttpClientConfiguration
        context.getBean(DefaultHttpClientConfiguration).class == DefaultHttpClientConfiguration
        jdk.readTimeout.get().seconds == 7
        jdk.connectionPoolConfiguration.maxConcurrentHttp1Connections == 7
        jdk.http2Configuration.pingIntervalRead.seconds == 7
        jdk.jdk.decodeErrorType

        cleanup:
        context.close()
    }

    void 'the clients of the registry use the JDK settings'() {
        given:
        def context = ApplicationContext.run([
            'micronaut.http.client.jdk.use-micronaut-redirects': true,
            'micronaut.http.services.backend.url': 'http://localhost',
            'micronaut.http.services.backend.jdk.use-micronaut-redirects': false,
        ])
        def registry = context.getBean(DefaultJdkHttpClientRegistry)

        expect:
        ((DefaultJdkHttpClient) context.getBean(HttpClient)).transport.jdkConfiguration.useMicronautRedirects
        !registry.getClient(io.micronaut.http.client.HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1), 'backend', null)
            .transport.jdkConfiguration.useMicronautRedirects

        cleanup:
        context.close()
    }

    void 'a client given another configuration uses the default JDK settings'() {
        when:
        def client = new DefaultJdkHttpClient(URI.create('http://localhost'), new DefaultHttpClientConfiguration(), null,
            JdkHttpClientFactory.createDefaultMessageBodyHandlerRegistry(), io.micronaut.core.convert.ConversionService.SHARED)

        then:
        with(client.transport.jdkConfiguration) {
            !applyRequestTimeout
            !useMicronautRedirects
            !decodeErrorType
        }

        cleanup:
        client.close()
    }
}
