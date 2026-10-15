package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.SslContextHolder
import io.netty.handler.ssl.SslContext
import io.netty.util.ReferenceCounted
import spock.lang.Specification

class LoadBalancedClientCloseSpec extends Specification {

    def 'closing the context closes clients created for a URL and releases their SSL context'() {
        given:
        def ctx = ApplicationContext.run()
        DefaultHttpClient client = (DefaultHttpClient) ctx.createBean(HttpClient, new URL('http://localhost:1'))

        SslContextHolder holder = client.connectionManager().sslContextWrapper.takeRetained()
        SslContext sslContext = holder.sslContext()
        holder.release()

        expect:
        client.isRunning()
        sslContext != null

        when:
        ctx.close()

        then:
        !client.isRunning()
        !(sslContext instanceof ReferenceCounted) || ((ReferenceCounted) sslContext).refCnt() == 0
    }
}
