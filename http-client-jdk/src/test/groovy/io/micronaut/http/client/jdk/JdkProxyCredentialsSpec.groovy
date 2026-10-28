package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import spock.lang.AutoCleanup
import spock.lang.Specification

class JdkProxyCredentialsSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'micronaut.http.client.proxy-type'    : 'http',
            'micronaut.http.client.proxy-address' : '127.0.0.1:3128',
            'micronaut.http.client.proxy-username': 'gateway',
            'micronaut.http.client.proxy-password': 'secret',
    ])

    def 'proxy credentials are not given to an origin server challenge'() {
        given:
        def client = ctx.createBean(HttpClient, new URL('http://127.0.0.1:9999'))
        Authenticator authenticator = ((DefaultJdkHttpClient) client).transport().client.authenticator().get()

        when:
        def server = authenticator.requestPasswordAuthenticationInstance('127.0.0.1', InetAddress.loopbackAddress, 9999,
                'http', 'quotes', 'basic', new URL('http://127.0.0.1:9999/quote'), Authenticator.RequestorType.SERVER)
        def proxy = authenticator.requestPasswordAuthenticationInstance('127.0.0.1', InetAddress.loopbackAddress, 3128,
                'http', 'proxy', 'basic', new URL('http://127.0.0.1:9999/quote'), Authenticator.RequestorType.PROXY)

        then:
        proxy?.userName == 'gateway'
        server == null

        cleanup:
        client?.close()
    }
}
