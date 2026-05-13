package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import spock.lang.AutoCleanup
import spock.lang.Specification

class JdkBlockingHttpClientReuseSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()

    def "toBlocking reuses the same underlying java.net.http.HttpClient on every call"() {
        given:
        DefaultJdkHttpClient parent = ctx.createBean(HttpClient, new URL('http://localhost:65530')) as DefaultJdkHttpClient

        when:
        def blockingClients = (1..100).collect { parent.toBlocking() }

        then: 'every blocking client wraps the SAME java.net.http.HttpClient instance'
        blockingClients.every { (it as AbstractJdkHttpClient).@client.is(parent.@client) }

        cleanup:
        parent.close()
    }
}
