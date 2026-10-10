package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.DefaultHttpClientConfiguration
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
        with(context.getBean(DefaultHttpClientConfiguration).jdk) {
            applyRequestTimeout
            useMicronautRedirects
            decodeErrorType
        }
        with(context.getBean(ServiceHttpClientConfiguration, Qualifiers.byName('backend')).jdk) {
            !applyRequestTimeout
            useMicronautRedirects
            decodeErrorType
        }
        with(context.getBean(ServiceHttpClientConfiguration, Qualifiers.byName('other')).jdk) {
            applyRequestTimeout
            useMicronautRedirects
            decodeErrorType
        }

        cleanup:
        context.close()
    }
}
